package org.example.service.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.example.annotations.DbQueryTimer;
import org.example.config.DatabaseConfig;
import org.example.utility.PasswordUtil;

public class UserServiceImpl implements UserService {

    private void ensureNotAdmin(Connection conn, String targetUser) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT role FROM users WHERE username = ?")) {
            stmt.setString(1, targetUser);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if ("ROLE_ADMIN".equals(rs.getString("role"))) {
                        throw new IllegalArgumentException("Action denied: Cannot modify or delete an administrator");
                    }
                } else {
                    throw new IllegalArgumentException("User not found");
                }
            }
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "BLOCK_USER")
    public void blockUser(String targetUser, boolean block) throws Exception {
        if (targetUser == null || !targetUser.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Invalid username format");
        }
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            ensureNotAdmin(conn, targetUser);
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET is_blocked = ? WHERE username = ?")) {
                stmt.setBoolean(1, block);
                stmt.setString(2, targetUser);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "CHANGE_USER_ROLE")
    public void changeRole(String targetUser, String role) throws Exception {
        if (targetUser == null || !targetUser.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Invalid username format");
        }
        if (role == null || (!role.equals("ROLE_ADMIN") && !role.equals("ROLE_READER"))) {
            throw new IllegalArgumentException("Invalid role");
        }
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            ensureNotAdmin(conn, targetUser);
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET role = ? WHERE username = ?")) {
                stmt.setString(1, role);
                stmt.setString(2, targetUser);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "CREATE_USER")
    public void createUser(String targetUser, String password, String role) throws Exception {
        if (targetUser == null || !targetUser.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Invalid username format (3-20 alphanumeric characters)");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        if (role == null || (!role.equals("ROLE_ADMIN") && !role.equals("ROLE_READER"))) {
            throw new IllegalArgumentException("Invalid role");
        }

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                checkStmt.setString(1, targetUser);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("User with this username already exists");
                    }
                }
            }

            String salt = PasswordUtil.generateSalt();
            String hashed = PasswordUtil.hashPassword(password, salt);
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password, salt, role) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, targetUser);
                stmt.setString(2, hashed);
                stmt.setString(3, salt);
                stmt.setString(4, role);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "DELETE_USER")
    public void deleteUser(String targetUser) throws Exception {
        if (targetUser == null || !targetUser.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new IllegalArgumentException("Invalid username format");
        }
        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            ensureNotAdmin(conn, targetUser);
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
                stmt.setString(1, targetUser);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "SEARCH_USERS")
    public Map<String, Object> searchUsers(String query, int limit, int offset) throws Exception {
        String searchQuery = (query == null || query.trim().isEmpty()) ? "%" : "%" + query.trim() + "%";
        int total = 0;
        List<Map<String, Object>> users = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
            try (PreparedStatement countStmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username ILIKE ?")) {
                countStmt.setString(1, searchQuery);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getInt(1);
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement("SELECT username, role, is_blocked FROM users WHERE username ILIKE ? ORDER BY username LIMIT ? OFFSET ?")) {
                stmt.setString(1, searchQuery);
                stmt.setInt(2, limit);
                stmt.setInt(3, offset);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("username", rs.getString("username"));
                        user.put("role", rs.getString("role"));
                        user.put("isBlocked", rs.getBoolean("is_blocked"));
                        users.add(user);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("users", users);
        return result;
    }
}
