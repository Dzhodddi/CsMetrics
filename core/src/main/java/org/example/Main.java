package org.example;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.example.config.DatabaseConfig;
import org.example.db.QueryExecutor;
import org.example.dtos.MetricDto;
import org.example.tcp.TcpMetricServer;

public class Main {

    public static void main(String[] args) throws Exception {
        DatabaseConfig.getDataSource();
        int tcpPort = Integer.parseInt(System.getenv().getOrDefault("TCP_PORT", "9090"));
        TcpMetricServer tcpServer = new TcpMetricServer(tcpPort, Main::saveMetrics);
        tcpServer.start();
        System.out.println("TCP Metric Server listening on port " + tcpPort);
        Thread.currentThread().join();
    }

    private static void saveMetrics(List<MetricDto> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

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

            executor.execQuery(sql, Map.of(
                    "ids", connection.createArrayOf("uuid", ids),
                    "classNames", connection.createArrayOf("varchar", classNames),
                    "methodNames", connection.createArrayOf("varchar", methodNames),
                    "durationsNs", connection.createArrayOf("bigint", durations)
            ));
        } catch (SQLException e) {
            System.err.println("Failed to batch insert metrics: " + e.getMessage());
        }
    }
}
