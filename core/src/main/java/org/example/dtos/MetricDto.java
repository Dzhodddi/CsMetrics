package org.example.dtos;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public static final RowMapper<MetricDto> ROW_MAPPER = MetricDto::fromResultSet;

    private static MetricDto fromResultSet(ResultSet rs) throws SQLException {
        OffsetDateTime recordedAt =
                rs.getObject("recorded_at", OffsetDateTime.class);

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