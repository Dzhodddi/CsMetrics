package org.example.dtos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.example.db.RowMapper;

public record MetricDto(
        UUID id,
        OffsetDateTime recordedAt,
        String environment,
        String hostName,
        String className,
        String methodName,
        Long durationNs,
        String metadata,
        boolean secured
) {
    public static final RowMapper<MetricDto> ROW_MAPPER = MetricDto::fromResultSet;

    private static MetricDto fromResultSet(ResultSet rs) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp("recorded_at");
        OffsetDateTime recordedAt = ts != null ? ts.toInstant().atOffset(ZoneOffset.UTC) : null;

        Object rawId = rs.getObject("id");
        UUID id = rawId == null ? null : (rawId instanceof UUID u ? u : UUID.fromString(rawId.toString()));

        long duration = rs.getLong("duration_ns");
        Long durationNs = rs.wasNull() ? null : duration;

        return new MetricDto(
                id, recordedAt,
                rs.getString("environment"),
                rs.getString("host_name"),
                rs.getString("class_name"),
                rs.getString("method_name"),
                durationNs,
                rs.getString("metadata"),
                rs.getBoolean("secured")
        );
    }
}