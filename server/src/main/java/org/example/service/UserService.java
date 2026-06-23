package org.example.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.config.DatabaseConfig;
import org.example.utility.PasswordUtil;

public class UserService implements IUserService {

    @Override
    @HttpRequestTimer(path = "/api/v1/admin/users (BLOCK)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "BLOCK_USER")
    public void blockUser(String targetUser, boolean block) throws Exception {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE users SET is_blocked = ? WHERE username = ?")) {
            stmt.setBoolean(1, block);
            stmt.setString(2, targetUser);
            stmt.executeUpdate();
        }
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/admin/users (ROLE)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "CHANGE_USER_ROLE")
    public void changeRole(String targetUser, String role) throws Exception {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE users SET role = ? WHERE username = ?")) {
            stmt.setString(1, role);
            stmt.setString(2, targetUser);
            stmt.executeUpdate();
        }
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/admin/users (CREATE)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "CREATE_USER")
    public void createUser(String targetUser, String password, String role) throws Exception {
        String salt = PasswordUtil.generateSalt();
        String hashed = PasswordUtil.hashPassword(password, salt);
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password, salt, role) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, targetUser);
            stmt.setString(2, hashed);
            stmt.setString(3, salt);
            stmt.setString(4, role);
            stmt.executeUpdate();
        }
    }
}
