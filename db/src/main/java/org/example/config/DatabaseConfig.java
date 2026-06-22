package org.example.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseConfig {
    private static HikariDataSource dataSource;

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();

            String dbUrl = System.getenv("DB_URL");

            if (dbUrl == null || dbUrl.isEmpty()) {
                dbUrl = "jdbc:postgresql://127.0.0.1:5432/metrics_db";
            }

            config.setJdbcUrl(dbUrl);
            config.setUsername("postgres");
            config.setPassword("postgres");
            config.setMaximumPoolSize(10);

            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            try (Database db = new Database(dataSource)) {
            } catch (Exception e) {
                throw new RuntimeException("Не вдалося ініціалізувати базу даних", e);
            }
        }
        return dataSource;
    }
}
