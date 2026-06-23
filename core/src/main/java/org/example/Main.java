package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.example.config.DatabaseConfig;
import org.example.dtos.MetricDto;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String SERVER_EXPORT_URL = "http://127.0.0.1:8080/api/v1/internal/export-metrics";

    public static void main(String[] args) throws Exception {
        int port = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/v1/metrics", exchange -> {
            if (handleCorsAndOptions(exchange)) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(SERVER_EXPORT_URL))
                                .GET()
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 200) {
                            List<MetricDto> freshlyPulledMetrics = mapper.readValue(response.body(), new TypeReference<List<MetricDto>>() {});

                            String insertSql = "INSERT INTO metrics (id, recorded_at, environment, host_name, class_name, method_name, duration_ns, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING";
                            try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                                for (MetricDto dto : freshlyPulledMetrics) {
                                    stmt.setObject(1, dto.id());
                                    stmt.setObject(2, dto.recordedAt());
                                    stmt.setString(3, dto.environment());
                                    stmt.setString(4, dto.hostName());
                                    stmt.setString(5, dto.className());
                                    stmt.setString(6, dto.methodName());
                                    stmt.setLong(7, dto.durationNs());
                                    stmt.setString(8, dto.metadata());
                                    stmt.addBatch();
                                }
                                stmt.executeBatch();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Scraping server metrics failed: " + e.getMessage());
                    }

                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = new HashMap<>();
                    if (query != null) {
                        for (String param : query.split("&")) {
                            String[] entry = param.split("=");
                            if (entry.length > 1) params.put(entry[0], entry[1]);
                        }
                    }

                    int page = Integer.parseInt(params.getOrDefault("page", "1"));
                    int size = Integer.parseInt(params.getOrDefault("size", "8"));
                    String methodFilter = params.get("method");
                    String fromFilter = params.get("from");
                    String toFilter = params.get("to");

                    StringBuilder sql = new StringBuilder("SELECT * FROM metrics WHERE 1=1");
                    List<Object> jdbcParams = new ArrayList<>();

                    if (methodFilter != null && !methodFilter.isBlank()) {
                        sql.append(" AND method_name = ?");
                        jdbcParams.add(methodFilter);
                    }
                    if (fromFilter != null && !fromFilter.isBlank()) {
                        sql.append(" AND recorded_at >= ?");
                        jdbcParams.add(OffsetDateTime.parse(fromFilter));
                    }
                    if (toFilter != null && !toFilter.isBlank()) {
                        sql.append(" AND recorded_at <= ?");
                        jdbcParams.add(OffsetDateTime.parse(toFilter));
                    }

                    String countSql = sql.toString().replace("SELECT *", "SELECT COUNT(*)");
                    int totalItems = 0;
                    try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                         PreparedStatement stmt = conn.prepareStatement(countSql)) {
                        for (int i = 0; i < jdbcParams.size(); i++) {
                            stmt.setObject(i + 1, jdbcParams.get(i));
                        }
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) totalItems = rs.getInt(1);
                        }
                    }

                    sql.append(" ORDER BY recorded_at DESC LIMIT ? OFFSET ?");
                    jdbcParams.add(size);
                    jdbcParams.add((page - 1) * size);

                    List<MetricDto> data = new ArrayList<>();
                    try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                        for (int i = 0; i < jdbcParams.size(); i++) {
                            stmt.setObject(i + 1, jdbcParams.get(i));
                        }
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                data.add(new MetricDto(
                                        (UUID) rs.getObject("id"),
                                        rs.getObject("recorded_at", OffsetDateTime.class),
                                        rs.getString("environment"),
                                        rs.getString("host_name"),
                                        rs.getString("class_name"),
                                        rs.getString("method_name"),
                                        rs.getLong("duration_ns"),
                                        rs.getString("metadata")
                                ));
                            }
                        }
                    }

                    Map<String, Object> responseMap = new HashMap<>();
                    responseMap.put("data", data);
                    responseMap.put("currentPage", page);
                    responseMap.put("totalPages", totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / size));
                    responseMap.put("totalItems", totalItems);

                    sendResponse(exchange, 200, mapper.writeValueAsString(responseMap));
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Core query failed\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
    }

    private static boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin != null ? origin : "http://127.0.0.1:3000");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return true;
        }
        return false;
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
}
