package org.example.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryExecutor<T> {

    private final Connection connection;
    private final RowMapper<T> rowMapper;

    private static final Pattern PARAM_PATTERN = Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    public QueryExecutor(Connection connection, RowMapper<T> rowMapper) {
        this.connection = connection;
        this.rowMapper = rowMapper;
    }

    public void execQuery(String sql, Map<String, Object> params) throws SQLException {
        ParsedQuery parsedQuery = parseSql(sql);
        try (PreparedStatement stmt = connection.prepareStatement(parsedQuery.jdbcSql)) {
            setParameters(stmt, parsedQuery.paramNames, params);
            stmt.execute();
        }
    }

    public int insert(String sql, Map<String, Object> params) throws SQLException {
        ParsedQuery parsedQuery = parseSql(sql);
        try (PreparedStatement stmt = connection.prepareStatement(parsedQuery.jdbcSql)) {
            setParameters(stmt, parsedQuery.paramNames, params);
            return stmt.executeUpdate();
        }
    }

    public List<T> selectList(String sql, Map<String, Object> params) throws SQLException {
        List<T> results = new ArrayList<>();
        ParsedQuery parsedQuery = parseSql(sql);

        try (PreparedStatement stmt = connection.prepareStatement(parsedQuery.jdbcSql)) {
            setParameters(stmt, parsedQuery.paramNames, params);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
            }
        }
        return results;
    }


    public T selectExactlyOne(String sql, Map<String, Object> params) throws SQLException {
        return selectOne(sql, params)
                .orElseThrow(() -> new SQLException("Expected exactly 1 result, but found 0"));
    }

    private Optional<T> selectOne(String sql, Map<String, Object> params) throws SQLException {
        List<T> results = selectList(sql, params);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new SQLException("Expected 0 or 1 result, but found " + results.size());
        }
        return Optional.of(results.getFirst());
    }

    private ParsedQuery parseSql(String sql) {
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        List<String> paramNames = new ArrayList<>();
        StringBuilder jdbcSql = new StringBuilder();

        while (matcher.find()) {
            paramNames.add(matcher.group(1));
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);

        return new ParsedQuery(jdbcSql.toString(), paramNames);
    }

    private void setParameters(PreparedStatement stmt, List<String> orderedNames, Map<String, Object> params) throws SQLException {
        for (int i = 0; i < orderedNames.size(); i++) {
            String paramName = orderedNames.get(i);
            if (!params.containsKey(paramName)) {
                throw new IllegalArgumentException("Missing value for SQL parameter: '" + paramName + "'");
            }
            stmt.setObject(i + 1, params.get(paramName));
        }
    }

    private record ParsedQuery(String jdbcSql, List<String> paramNames) {}
}