package org.example;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.dtos.LoginDto;
import org.example.dtos.MetricDto;
import org.example.dtos.TokenResponse;
import org.example.proxies.ProxyFactory;
import org.example.reporters.MetricsReporter;
import org.example.repository.MetricRepository;
import org.example.service.DataService;
import org.example.service.DefaultDataService;
import org.example.service.MetricService;
import org.example.config.DatabaseConfig;
import org.example.utility.JwtUtil;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        System.out.println("Starting metrics producer on HttpServer...");

        MetricRepository metricRepository = new MetricRepository(DatabaseConfig.getDataSource());
        MetricService metricService = new MetricService(metricRepository);
        DefaultDataService realDataService = new DefaultDataService();

        Map<Class<? extends Annotation>, MetricsReporter> reporters = new HashMap<>();
        reporters.put(HttpRequestTimer.class, (annotation, methodName, durationMs) -> {
            HttpRequestTimer httpAnn = (HttpRequestTimer) annotation;
            Long durationNs = durationMs * 1_000_000L;
            String jsonMetadata = String.format("{\"api_path\": \"%s\", \"type\": \"http_request\"}", httpAnn.path());

            MetricDto dto = new MetricDto(UUID.randomUUID(), OffsetDateTime.now(ZoneId.of("Europe/Kyiv")), "DEV", "localhost",
                    realDataService.getClass().getName(), methodName, durationNs, jsonMetadata);
            metricService.recordMetric(dto);
        });

        reporters.put(DbQueryTimer.class, (annotation, methodName, durationMs) -> {
            DbQueryTimer dbAnn = (DbQueryTimer) annotation;
            Long durationNs = durationMs * 1_000_000L;
            String jsonMetadata = String.format("{\"database\": \"%s\", \"query_action\": \"%s\"}", dbAnn.dbName(), dbAnn.queryAction());

            MetricDto dto = new MetricDto(UUID.randomUUID(), OffsetDateTime.now(ZoneId.of("Europe/Kyiv")), "DEV", "localhost",
                    realDataService.getClass().getName(), methodName, durationNs, jsonMetadata);
            metricService.recordMetric(dto);
        });

        DataService proxyDataService = ProxyFactory.createProxy(realDataService, DataService.class, reporters);

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/v1/login", exchange -> {
            if (handleCorsAndOptions(exchange)) return;

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    LoginDto loginDto = mapper.readValue(exchange.getRequestBody(), LoginDto.class);

                    try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT password, role FROM users WHERE username = ?")) {
                        stmt.setString(1, loginDto.username());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next() && rs.getString("password").equals(loginDto.password())) {
                                String role = rs.getString("role");
                                String token = JwtUtil.createJwt(loginDto.username(), role);
                                sendResponse(exchange, 200, mapper.writeValueAsString(new TokenResponse(token)));
                                return;
                            }
                        }
                    }
                    sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Invalid credentials\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"Bad request format\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/validate", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.fastValidation();
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Validation executed. Metric spawned!\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/load-data", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.loadData();
                sendResponse(exchange, 200, "{\"status\":\"success\", \"action\":\"db_data_loaded\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/metrics", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    java.util.List<MetricDto> allMetrics = metricService.getMetrics();

                    String query = exchange.getRequestURI().getQuery();
                    int page = 1;
                    int size = 8;

                    if (query != null) {
                        java.util.Map<String, String> params = new java.util.HashMap<>();
                        for (String param : query.split("&")) {
                            String[] entry = param.split("=");
                            if (entry.length > 1) {
                                params.put(entry[0], entry[1]);
                            }
                        }
                        if (params.containsKey("page")) page = Integer.parseInt(params.get("page"));
                        if (params.containsKey("size")) size = Integer.parseInt(params.get("size"));
                    }

                    int totalItems = allMetrics.size();
                    int fromIndex = (page - 1) * size;
                    int toIndex = Math.min(fromIndex + size, totalItems);

                    java.util.List<MetricDto> pagedMetrics;
                    if (fromIndex >= totalItems || fromIndex < 0) {
                        pagedMetrics = new java.util.ArrayList<>();
                    } else {
                        pagedMetrics = allMetrics.subList(fromIndex, toIndex);
                    }

                    java.util.Map<String, Object> responseMap = new java.util.HashMap<>();
                    responseMap.put("data", pagedMetrics);
                    responseMap.put("currentPage", page);
                    responseMap.put("totalPages", (int) Math.ceil((double) totalItems / size));
                    responseMap.put("totalItems", totalItems);

                    String jsonResponse = mapper.writeValueAsString(responseMap);
                    sendResponse(exchange, 200, jsonResponse);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\": \"Failed to fetch metrics\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server is running on http://127.0.0.1:" + port);
    }

    private static boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        } else {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://127.0.0.1:3000");
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static String validateJwtAndGetRole(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Missing or malformed token\"}");
            return null;
        }

        try {
            String token = authHeader.substring(7);
            DecodedJWT decodedJWT = JwtUtil.decodeJWT(token);
            return decodedJWT.getClaim("role").asString();
        } catch (Exception e) {
            sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Invalid token\"}");
            return null;
        }
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }
}
