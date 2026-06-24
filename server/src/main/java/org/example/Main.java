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
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import javax.crypto.SecretKey;
import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.config.DatabaseConfig;
import org.example.dtos.auth.LoginDto;
import org.example.dtos.MetricDto;
import org.example.dtos.card.SecureCardRequestDto;
import org.example.dtos.card.SecureCardResponseDto;
import org.example.metrics.MetricsRegistry;
import org.example.proxies.ProxyFactory;
import org.example.reporters.DbMetricsReporter;
import org.example.reporters.HttpMetricsReporter;
import org.example.reporters.MetricsReporter;
import org.example.service.auth.AuthService;
import org.example.tcp.TcpMetricClient;
import org.example.utility.AesUtil;
import org.example.utility.JwtUtil;
import org.example.service.data.DataService;
import org.example.service.data.DataServiceImpl;
import org.example.service.auth.AuthServiceImpl;
import org.example.service.card.CardServiceImpl;
import org.example.service.card.CardService;
import org.example.service.user.UserServiceImpl;
import org.example.service.user.UserService;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final ConcurrentLinkedQueue<MetricDto> metricsStorage = new ConcurrentLinkedQueue<>();
    private static final SecretKey dbEncryptionKey = AesUtil.getSecretKeyFromBytes(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );
    private static final int SERVER_PORT = 9090;
    private static final String SERVER_HOST = "app-core";

    public static void main(String[] args) throws Exception {
        DatabaseConfig.getDataSource();
        TcpMetricClient metricClient = new TcpMetricClient(SERVER_HOST, SERVER_PORT);
        DataService realDataService = new DataServiceImpl();
        CardService realCardService = new CardServiceImpl(dbEncryptionKey);
        UserService realUserService = new UserServiceImpl();

        MetricsRegistry registry = new MetricsRegistry(metricClient);
        Map<Class<? extends Annotation>, MetricsReporter> reporters = Map.of(
                DbQueryTimer.class, new DbMetricsReporter(registry),
                HttpRequestTimer.class, new HttpMetricsReporter(registry)
        );

        DataService proxyDataService = ProxyFactory.createProxy(realDataService, DataService.class, reporters);
        CardService cardService = ProxyFactory.createProxy(realCardService, CardService.class, reporters);
        UserService userService = ProxyFactory.createProxy(realUserService, UserService.class, reporters);
        AuthService authService = new AuthServiceImpl();

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        HttpRoutes routeProxy = ProxyFactory.createProxy(
                new HttpRoutesImpl(proxyDataService, cardService, userService, authService),
                HttpRoutes.class,
                reporters
        );

        server.createContext("/api/v1/internal/export-metrics", exchange -> {
            try { routeProxy.exportMetrics(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/login", exchange -> {
            try { routeProxy.login(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/validate", exchange -> {
            try { routeProxy.validate(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/load-data", exchange -> {
            try { routeProxy.loadData(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/cards", exchange -> {
            try { routeProxy.cards(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/cards/detail", exchange -> {
            try { routeProxy.cardsDetail(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/admin/users", exchange -> {
            try { routeProxy.adminUsers(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/metrics", exchange -> {
            try { routeProxy.metrics(exchange); } catch (Exception e) {}
        });

        server.createContext("/api/v1/login", exchange -> {
            try { routeProxy.login(exchange); } catch (Exception e) {}
        });

        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    public interface HttpRoutes {
        void exportMetrics(HttpExchange exchange) throws IOException;
        void login(HttpExchange exchange) throws IOException;
        void validate(HttpExchange exchange) throws IOException;
        void loadData(HttpExchange exchange) throws IOException;
        void cards(HttpExchange exchange) throws IOException;
        void cardsDetail(HttpExchange exchange) throws IOException;
        void adminUsers(HttpExchange exchange) throws IOException;
        void metrics(HttpExchange exchange) throws IOException;
    }

    public static class HttpRoutesImpl implements HttpRoutes {
        private final DataService proxyDataService;
        private final CardService cardService;
        private final UserService userService;
        private final AuthService authService;

        public HttpRoutesImpl(DataService proxyDataService,
                              CardService cardService,
                              UserService userService,
                              AuthService authService) {
            this.proxyDataService = proxyDataService;
            this.cardService = cardService;
            this.userService = userService;
            this.authService = authService;
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/internal/export-metrics")
        public void exportMetrics(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<MetricDto> exportedList = new ArrayList<>();
                MetricDto metric;
                while ((metric = metricsStorage.poll()) != null) {
                    exportedList.add(metric);
                }
                sendResponse(exchange, 200, mapper.writeValueAsString(exportedList));
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/login")
        public void login(HttpExchange exchange) throws IOException {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin != null ? origin : "http://127.0.0.1:3000");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    LoginDto loginDto = mapper.readValue(exchange.getRequestBody(), LoginDto.class);
                    String token = authService.authenticateAndGetToken(loginDto);

                    String response = "{\"token\":\"" + token + "\"}";
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                } catch (Exception e) {
                    byte[] error = "{\"error\": \"Unauthorized\"}".getBytes();
                    exchange.sendResponseHeaders(401, error.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(error); }
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/validate", secured = true)
        public void validate(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;
            if (!"ROLE_ADMIN".equals(role)) {
                sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.fastValidation();
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Validation executed. Metric spawned!\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/load-data", secured = true)
        public void loadData(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;
            if (!"ROLE_ADMIN".equals(role)) {
                sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.loadData();
                sendResponse(exchange, 200, "{\"status\":\"success\", \"action\":\"db_data_loaded\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/cards", secured = true)
        public void cards(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (role == null) {
                return;
            }

            String method = exchange.getRequestMethod();
            try {
                if ("POST".equalsIgnoreCase(method)) {
                    if (!"ROLE_ADMIN".equals(role)) {
                        sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                        return;
                    }
                    SecureCardRequestDto input = mapper.readValue(exchange.getRequestBody(), SecureCardRequestDto.class);
                    try {
                        UUID newId = cardService.createCard(input);
                        sendResponse(exchange, 201, "{\"status\":\"created\", \"id\":\"" + newId + "\"}");
                    } catch (IllegalArgumentException ex) {
                        sendResponse(exchange, 400, "{\"error\": \"" + ex.getMessage() + "\"}");
                    }
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    List<SecureCardResponseDto> cards = cardService.getCards(role);
                    sendResponse(exchange, 200, mapper.writeValueAsString(cards));
                    return;
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"Bad request\"}");
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/cards/detail", secured = true)
        public void cardsDetail(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (role == null) {
                return;
            }

            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            UUID cardId = null;

            if (query != null && query.contains("id=")) {
                cardId = UUID.fromString(query.split("id=")[1].split("&")[0]);
            }

            if (cardId == null) {
                sendResponse(exchange, 400, "{\"error\": \"Missing id parameter\"}");
                return;
            }

            try {
                if ("PUT".equalsIgnoreCase(method)) {
                    if (!"ROLE_ADMIN".equals(role)) {
                        sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                        return;
                    }
                    SecureCardRequestDto input = mapper.readValue(exchange.getRequestBody(), SecureCardRequestDto.class);
                    try {
                        boolean updated = cardService.updateCard(cardId, input);
                        if (updated) {
                            sendResponse(exchange, 200, "{\"status\": \"updated\"}");
                        } else {
                            sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                        }
                    } catch (IllegalArgumentException ex) {
                        sendResponse(exchange, 400, "{\"error\": \"" + ex.getMessage() + "\"}");
                    }
                    return;
                }

                if ("DELETE".equalsIgnoreCase(method)) {
                    if (!"ROLE_ADMIN".equals(role)) {
                        sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                        return;
                    }
                    boolean deleted = cardService.deleteCard(cardId);
                    if (deleted) {
                        sendResponse(exchange, 200, "{\"status\": \"deleted\"}");
                    } else {
                        sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                    }
                    return;
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"Operation failed\"}");
            }
        }

        @Override
        @HttpRequestTimer(path = "/api/v1/admin/users", secured = true)
        public void adminUsers(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (!"ROLE_ADMIN".equals(role)) {
                sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                int page = 1;
                int size = 2;
                String searchQuery = "";

                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            if (pair[0].equals("page")) page = Integer.parseInt(pair[1]);
                            if (pair[0].equals("size")) size = Integer.parseInt(pair[1]);
                            if (pair[0].equals("query")) searchQuery = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        }
                    }
                }

                int offset = (page - 1) * size;

                try {
                    Map<String, Object> searchResult = userService.searchUsers(searchQuery, size, offset);
                    int totalCount = (int) searchResult.get("total");
                    int totalPages = (int) Math.ceil((double) totalCount / size);

                    Map<String, Object> response = new HashMap<>();
                    response.put("currentPage", page);
                    response.put("totalPages", totalPages);
                    response.put("data", searchResult.get("users"));

                    sendResponse(exchange, 200, mapper.writeValueAsString(response));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Failed to fetch users\"}");
                }

            } else if ("POST".equalsIgnoreCase(method)) {
                try {
                    Map<String, Object> req = mapper.readValue(exchange.getRequestBody(), Map.class);
                    String targetUser = (String) req.get("username");
                    String action = (String) req.get("action");

                    if ("block".equalsIgnoreCase(action)) {
                        userService.blockUser(targetUser, (Boolean) req.get("block"));
                    } else if ("change_role".equalsIgnoreCase(action)) {
                        userService.changeRole(targetUser, (String) req.get("role"));
                    } else if ("create".equalsIgnoreCase(action)) {
                        userService.createUser(targetUser, (String) req.get("password"), (String) req.get("role"));
                    } else if ("delete".equalsIgnoreCase(action)) {
                        userService.deleteUser(targetUser);
                    }
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"User action processed\"}");
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, "{\"error\": \"" + e.getMessage() + "\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"Invalid admin request structure\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }

        @Override
        public void metrics(HttpExchange exchange) throws IOException {
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                int page = 1;
                int size = 2;

                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length == 2) {
                            if (pair[0].equals("page")) page = Integer.parseInt(pair[1]);
                            if (pair[0].equals("size")) size = Integer.parseInt(pair[1]);
                        }
                    }
                }

                int offset = (page - 1) * size;

                try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                    int totalCount = 0;
                    try (PreparedStatement countStmt = conn.prepareStatement("SELECT COUNT(*) FROM metrics");
                         ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) {
                            totalCount = rs.getInt(1);
                        }
                    }
                    int totalPages = (int) Math.ceil((double) totalCount / size);

                    List<Map<String, Object>> data = new ArrayList<>();
                    String sql = "SELECT * FROM metrics ORDER BY recorded_at DESC LIMIT ? OFFSET ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, size);
                        stmt.setInt(2, offset);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> map = new HashMap<>();
                                map.put("id", rs.getString("id"));
                                map.put("className", rs.getString("class_name"));
                                map.put("methodName", rs.getString("method_name"));
                                map.put("durationNs", rs.getLong("duration_ns"));
                                map.put("recordedAt", rs.getTimestamp("recorded_at").getTime());
                                map.put("environment", "PROD");
                                map.put("hostName", "app-core");
                                data.add(map);
                            }
                        }
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("currentPage", page);
                    result.put("totalPages", totalPages);
                    result.put("data", data);

                    sendResponse(exchange, 200, mapper.writeValueAsString(result));

                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Database error while fetching metrics\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        }
    }
}
