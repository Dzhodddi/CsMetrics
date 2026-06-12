package org.example.utility;

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

            statement.execute(
                    """
                     CREATE TABLE IF NOT EXISTS agents (
                     id SERIAL PRIMARY KEY,
                     name VARCHAR(255) NOT NULL,
                     ip_address VARCHAR(50) NOT NULL,
                     status VARCHAR(20) DEFAULT 'ACTIVE',
                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                     )
                     """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS metrics (
                 id BIGSERIAL PRIMARY KEY,
                 agent_id INT REFERENCES agents(id),
                 metric_type VARCHAR(50) NOT NULL,
                 metric_value NUMERIC NOT NULL,
                 recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password_hash VARCHAR(128) NOT NULL,
                    salt VARCHAR(64) NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'READER',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS tokens (
                    id SERIAL PRIMARY KEY,
                    user_id INT REFERENCES users(id) ON DELETE CASCADE,
                    token VARCHAR(255) UNIQUE NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS aggregated_metrics (
                    id BIGSERIAL PRIMARY KEY,
                    agent_id INT REFERENCES agents(id) ON DELETE CASCADE,
                    metric_type VARCHAR(50) NOT NULL,
                    avg_value NUMERIC,
                    min_value NUMERIC,
                    max_value NUMERIC,
                    count_samples INT,
                    period_start TIMESTAMP NOT NULL,
                    period_end TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Database init failed", e);
        }
    }

    @Override
    public void close() {
    }
}
