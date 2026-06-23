package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.example.config.DatabaseConfig;
import org.example.db.QueryExecutor;
import org.example.dtos.MetricDto;
import org.example.tcp.TcpMetricServer;

public class Main {
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String SERVER_BASE_URL = "http://127.0.0.1:8080/api/v1";

    public static void main(String[] args) throws Exception {
        DatabaseConfig.getDataSource();

        int tcpPort = Integer.parseInt(System.getenv().getOrDefault("TCP_PORT", "9090"));
        TcpMetricServer tcpServer = new TcpMetricServer(tcpPort, Main::saveMetrics);
        tcpServer.start();
        System.out.println("TCP Metric Server listening on port " + tcpPort);

        int port = 8081;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
    }

    private static boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin != null ? origin : "http://127.0.0.1:3000");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Client-ID");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void saveMetrics(List<MetricDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }

        Object[] ids = dtos.stream().map(MetricDto::id).toArray();
        String[] classNames = dtos.stream().map(MetricDto::className).toArray(String[]::new);
        String[] methodNames = dtos.stream().map(MetricDto::methodName).toArray(String[]::new);
        Object[] durations = dtos.stream().map(MetricDto::durationNs).toArray();

        try (Connection connection = DatabaseConfig.getDataSource().getConnection()) {
            var executor = new QueryExecutor<>(connection, MetricDto.ROW_MAPPER);

            String sql = """
                INSERT INTO metrics (id, recorded_at, class_name, method_name, duration_ns) 
                SELECT id_elem, CURRENT_TIMESTAMP, class_elem, method_elem, duration_elem 
                FROM unnest(:ids, :classNames, :methodNames, :durationsNs) 
                  AS t(id_elem, class_elem, method_elem, duration_elem)
            """;

            executor.execQuery(sql,Map.of(
                    "ids", connection.createArrayOf("uuid", ids),
                    "classNames", connection.createArrayOf("varchar", classNames),
                    "methodNames", connection.createArrayOf("varchar", methodNames),
                    "durationsNs", connection.createArrayOf("bigint", durations)
            ));

        } catch (SQLException e) {
            System.err.println("Failed to batch insert metrics: " + e.getMessage());
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
}
