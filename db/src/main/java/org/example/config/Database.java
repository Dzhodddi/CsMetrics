package org.example.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Database implements AutoCloseable {

    private final DataSource dataSource;

    public Database(DataSource dataSource) {
        this.dataSource = dataSource;
        init();
    }

    private void init() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password VARCHAR(128) NOT NULL,
                    role VARCHAR(20) NOT NULL
                )
                """);

            statement.execute("""
                INSERT INTO users (username, password, role) VALUES 
                ('admin', 'admin123', 'ROLE_ADMIN'),
                ('user', 'user123', 'ROLE_USER')
                ON CONFLICT (username) DO NOTHING
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS metrics (
                    id UUID PRIMARY KEY,
                    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    environment VARCHAR(50),
                    host_name VARCHAR(100),
                    class_name VARCHAR(255),
                    method_name VARCHAR(255),
                    duration_ns BIGINT,
                    metadata JSONB
                )
                """);

            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_metrics_recorded_at 
                ON metrics (recorded_at DESC)
                """);

            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_metrics_method_time 
                ON metrics (class_name, method_name, recorded_at DESC)
                """);

            System.out.println("Database schema initialized successfully.");

        } catch (SQLException e) {
            throw new RuntimeException("Database init failed", e);
        }
    }

    @Override
    public void close() {
    }
}
