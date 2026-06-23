package org.example.config;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.example.utility.PasswordUtil;

public class Database implements AutoCloseable {

    private final DataSource dataSource;

    public Database(DataSource dataSource) {
        this.dataSource = dataSource;
        init();
    }

    private void init() {
        try (Connection connection = dataSource.getConnection()) {

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                     id SERIAL PRIMARY KEY,
                     username VARCHAR(50) UNIQUE NOT NULL,
                     password VARCHAR(128) NOT NULL,
                     salt VARCHAR(50) NOT NULL,
                     role VARCHAR(20) NOT NULL,
                     is_blocked BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
            }

            try (Statement stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    insertUser(connection, "admin", "admin123", "ROLE_ADMIN");
                    insertUser(connection, "user", "user123", "ROLE_READER");
                }
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                CREATE TABLE IF NOT EXISTS secure_cards (
                    id UUID PRIMARY KEY,
                    title VARCHAR(100) NOT NULL,
                    holder_name VARCHAR(100) NOT NULL,
                    encrypted_card_number VARCHAR(255) NOT NULL,
                    encrypted_cvv VARCHAR(50) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
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
            }

            System.out.println("Database schema initialized successfully.");
        } catch (SQLException e) {
            throw new RuntimeException("Database init failed", e);
        }
    }
    private void insertUser(Connection conn, String username, String password, String role) throws SQLException {
        String salt = PasswordUtil.generateSalt();
        String hashedPassword = PasswordUtil.hashPassword(password, salt);

        String sql = "INSERT INTO users (username, password, salt, role) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, salt);
            pstmt.setString(4, role);
            pstmt.executeUpdate();
        }
    }

    @Override
    public void close() {}
}
