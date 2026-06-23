package org.example.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.example.db.QueryExecutor;
import org.example.dtos.MetricDto;

public class MetricRepository {

    private final DataSource dataSource;

    public MetricRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(MetricDto metric) throws SQLException {
        String sql = """
            INSERT INTO metrics
            (id, environment, host_name, class_name, method_name, duration_ns, metadata, secured)
            VALUES (:id, :environment, :hostName, :className, :methodName, :durationNs, :metadata::jsonb, :secured)
            """;

        Map<String, Object> params = new HashMap<>();
        params.put("id", metric.id());
        params.put("environment", metric.environment());
        params.put("hostName", metric.hostName());
        params.put("className", metric.className());
        params.put("methodName", metric.methodName());
        params.put("durationNs", metric.durationNs() != null ? metric.durationNs() : java.sql.Types.NULL);
        params.put("metadata", metric.metadata() != null ? metric.metadata() : "{}");
        params.put("secured", metric.secured());

        try (Connection conn = dataSource.getConnection()) {
            QueryExecutor<MetricDto> executor = new QueryExecutor<>(conn, MetricDto.ROW_MAPPER);
            executor.insert(sql, params);
        }
    }

    public List<MetricDto> findAll() throws SQLException {
        String sql = """
                    SELECT id, recorded_at, environment, host_name, class_name, method_name, duration_ns, metadata, secured 
                    FROM metrics
                    ORDER BY recorded_at DESC
                    LIMIT 1000
                    """;

        try (Connection conn = dataSource.getConnection()) {
            QueryExecutor<MetricDto> executor = new QueryExecutor<>(conn, MetricDto.ROW_MAPPER);
            return executor.selectList(sql, Map.of());
        }
    }
}