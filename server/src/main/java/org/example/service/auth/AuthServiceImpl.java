package org.example.service.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.example.config.DatabaseConfig;
import org.example.dtos.auth.LoginDto;
import org.example.utility.JwtUtil;
import org.example.utility.PasswordUtil;

public class AuthServiceImpl implements AuthService {

    @Override
    public String authenticateAndGetToken(LoginDto loginDto) throws Exception {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password, salt, role, is_blocked FROM users WHERE username = ?")) {

            stmt.setString(1, loginDto.username());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    if (rs.getBoolean("is_blocked")) {
                        throw new Exception("User is blocked");
                    }

                    String storedHash = rs.getString("password");
                    String salt = rs.getString("salt");
                    String role = rs.getString("role");

                    String computedHash = PasswordUtil.hashPassword(loginDto.password(), salt);

                    if (storedHash.equals(computedHash)) {
                        return JwtUtil.createJwt(loginDto.username(), role);
                    }
                }
            }
        }
        throw new Exception("Unauthorized: Invalid credentials");
    }
}
