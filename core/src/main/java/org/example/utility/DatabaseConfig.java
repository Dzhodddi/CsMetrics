package org.example.utility;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public class DatabaseConfig {
    private static HikariDataSource dataSource;

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/metrics_db");
            config.setUsername("postgres");
            config.setPassword("postgres");
            config.setMaximumPoolSize(10);

            dataSource = new HikariDataSource(config);

            try (Database db = new Database(dataSource)) {
            } catch (Exception e) {
                throw new RuntimeException("Не вдалося ініціалізувати базу даних", e);
            }
        }
        return dataSource;
    }
}
