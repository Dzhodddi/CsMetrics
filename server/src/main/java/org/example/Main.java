package org.example;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import javax.crypto.SecretKey;
import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.dtos.LoginDto;
import org.example.dtos.MetricDto;
import org.example.dtos.SecureCardDto;
import org.example.dtos.TokenResponse;
import org.example.proxies.ProxyFactory;
import org.example.reporters.MetricsReporter;
import org.example.config.DatabaseConfig;
import org.example.utility.JwtUtil;
import org.example.utility.PasswordUtil;
import org.example.cryptography.RsaUtil;
import org.example.cryptography.AesUtil;
import org.example.cryptography.SessionKeyStore;
import org.example.service.DataService;
import org.example.service.DefaultDataService;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final KeyPair rsaKeyPair;
    private static final SessionKeyStore sessionKeyStore = new SessionKeyStore();

    private static final ConcurrentLinkedQueue<MetricDto> metricsStorage = new ConcurrentLinkedQueue<>();

    private static final SecretKey dbEncryptionKey = AesUtil.getSecretKeyFromBytes(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );

    static {
        try {
            rsaKeyPair = RsaUtil.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize server RSA keys", e);
        }
    }

    public static void main(String[] args) throws Exception {
        DefaultDataService realDataService = new DefaultDataService();
        Map<Class<? extends Annotation>, MetricsReporter> reporters = new HashMap<>();

        reporters.put(HttpRequestTimer.class, (annotation, methodName, durationMs) -> {
            HttpRequestTimer httpAnn = (HttpRequestTimer) annotation;
            Long durationNs = durationMs * 1_000_000L;
            String jsonMetadata = String.format("{\"api_path\": \"%s\", \"type\": \"http_request\"}", httpAnn.path());

            MetricDto dto = new MetricDto(UUID.randomUUID(), OffsetDateTime.now(ZoneId.of("Europe/Kyiv")), "DEV", "localhost",
                    realDataService.getClass().getName(), methodName, durationNs, jsonMetadata);
            metricsStorage.add(dto);
        });

        reporters.put(DbQueryTimer.class, (annotation, methodName, durationMs) -> {
            DbQueryTimer dbAnn = (DbQueryTimer) annotation;
            Long durationNs = durationMs * 1_000_000L;
            String jsonMetadata = String.format("{\"database\": \"%s\", \"query_action\": \"%s\"}", dbAnn.dbName(), dbAnn.queryAction());

            MetricDto dto = new MetricDto(UUID.randomUUID(), OffsetDateTime.now(ZoneId.of("Europe/Kyiv")), "DEV", "localhost",
                    realDataService.getClass().getName(), methodName, durationNs, jsonMetadata);
            metricsStorage.add(dto);
        });

        DataService proxyDataService = ProxyFactory.createProxy(realDataService, DataService.class, reporters);

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/v1/internal/export-metrics", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                List<MetricDto> exportedList = new ArrayList<>();
                MetricDto metric;
                while ((metric = metricsStorage.poll()) != null) {
                    exportedList.add(metric);
                }
                sendResponse(exchange, 200, mapper.writeValueAsString(exportedList));
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/crypto/public-key", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String publicKeyBase64 = Base64.getEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded());
                sendResponse(exchange, 200, "{\"publicKey\": \"" + publicKeyBase64 + "\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/crypto/handshake", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    JsonNode root = mapper.readTree(exchange.getRequestBody());
                    int clientId = root.get("clientId").asInt();
                    byte[] encryptedAesKey = Base64.getDecoder().decode(root.get("encryptedAesKey").asText());

                    byte[] decryptedAesKeyBytes = RsaUtil.decrypt(encryptedAesKey, rsaKeyPair.getPrivate());
                    SecretKey aesKey = AesUtil.getSecretKeyFromBytes(decryptedAesKeyBytes);

                    sessionKeyStore.saveKey(clientId, aesKey);
                    sendResponse(exchange, 200, "{\"status\": \"Key exchanged successfully\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"Handshake failed\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/login", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String clientIdHeader = exchange.getRequestHeaders().getFirst("X-Client-ID");
                    if (clientIdHeader == null) {
                        sendResponse(exchange, 400, "{\"error\": \"Missing X-Client-ID header\"}");
                        return;
                    }
                    int clientId = Integer.parseInt(clientIdHeader);
                    SecretKey aesKey = sessionKeyStore.getKey(clientId);
                    if (aesKey == null) {
                        sendResponse(exchange, 401, "{\"error\": \"Perform handshake first\"}");
                        return;
                    }

                    Map<String, String> bodyMap = mapper.readValue(exchange.getRequestBody(), Map.class);
                    byte[] encryptedBytes = Base64.getDecoder().decode(bodyMap.get("ciphertext"));
                    String decryptedJson = new String(AesUtil.decrypt(encryptedBytes, aesKey), StandardCharsets.UTF_8);

                    LoginDto loginDto = mapper.readValue(decryptedJson, LoginDto.class);

                    try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT password, salt, role, is_blocked FROM users WHERE username = ?")) {

                        stmt.setString(1, loginDto.username());

                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                if (rs.getBoolean("is_blocked")) {
                                    sendResponse(exchange, 403, "{\"error\": \"User is blocked\"}");
                                    return;
                                }

                                String storedHash = rs.getString("password");
                                String salt = rs.getString("salt");
                                String role = rs.getString("role");

                                String computedHash = PasswordUtil.hashPassword(loginDto.password(), salt);

                                if (storedHash.equals(computedHash)) {
                                    String token = JwtUtil.createJwt(loginDto.username(), role);
                                    String rawResponse = mapper.writeValueAsString(new TokenResponse(token));

                                    byte[] encryptedResponseBytes = AesUtil.encrypt(rawResponse.getBytes(StandardCharsets.UTF_8), aesKey);
                                    String encryptedResponseBase64 = Base64.getEncoder().encodeToString(encryptedResponseBytes);

                                    sendResponse(exchange, 200, "{\"ciphertext\": \"" + encryptedResponseBase64 + "\"}");
                                    return;
                                }
                            }
                        }
                    }
                    sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Invalid credentials\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"Bad request format or decryption error\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/validate", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.fastValidation();
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Validation executed. Metric spawned!\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/load-data", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                proxyDataService.loadData();
                sendResponse(exchange, 200, "{\"status\":\"success\", \"action\":\"db_data_loaded\"}");
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.createContext("/api/v1/cards", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            String method = exchange.getRequestMethod();
            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                if ("POST".equalsIgnoreCase(method)) {
                    SecureCardDto input = mapper.readValue(exchange.getRequestBody(), SecureCardDto.class);
                    UUID newId = UUID.randomUUID();

                    byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
                    byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

                    String encNumberBase64 = Base64.getEncoder().encodeToString(encNumberBytes);
                    String encCvvBase64 = Base64.getEncoder().encodeToString(encCvvBytes);

                    String sql = "INSERT INTO secure_cards (id, title, holder_name, encrypted_card_number, encrypted_cvv) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setObject(1, newId);
                        stmt.setString(2, input.title());
                        stmt.setString(3, input.holderName());
                        stmt.setString(4, encNumberBase64);
                        stmt.setString(5, encCvvBase64);
                        stmt.executeUpdate();
                    }

                    sendResponse(exchange, 201, "{\"status\":\"created\", \"id\":\"" + newId + "\"}");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    String sql = "SELECT id, title, holder_name, encrypted_card_number, encrypted_cvv FROM secure_cards";
                    try (PreparedStatement stmt = conn.prepareStatement(sql);
                         ResultSet rs = stmt.executeQuery()) {

                        List<SecureCardDto> cards = new ArrayList<>();
                        while (rs.next()) {
                            UUID id = (UUID) rs.getObject("id");
                            String title = rs.getString("title");
                            String holderName = rs.getString("holder_name");

                            String cardNumber = "[FORBIDDEN]";
                            String cvv = "***";

                            if ("ROLE_ADMIN".equals(role)) {
                                byte[] decNumberBytes = AesUtil.decrypt(Base64.getDecoder().decode(rs.getString("encrypted_card_number")), dbEncryptionKey);
                                byte[] decCvvBytes = AesUtil.decrypt(Base64.getDecoder().decode(rs.getString("encrypted_cvv")), dbEncryptionKey);

                                cardNumber = new String(decNumberBytes, StandardCharsets.UTF_8);
                                cvv = new String(decCvvBytes, StandardCharsets.UTF_8);
                            }

                            cards.add(new SecureCardDto(id, title, holderName, cardNumber, cvv));
                        }

                        sendResponse(exchange, 200, mapper.writeValueAsString(cards));
                        return;
                    }
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"Bad request or cryptography error\"}");
            }
        });

        server.createContext("/api/v1/cards/detail", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            UUID cardId = null;

            if (query != null && query.contains("id=")) {
                cardId = UUID.fromString(query.split("id=")[1].split("&")[0]);
            }

            if (cardId == null) {
                sendResponse(exchange, 400, "{\"error\": \"Missing id parameter\"}");
                return;
            }

            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                if ("PUT".equalsIgnoreCase(method)) {
                    SecureCardDto input = mapper.readValue(exchange.getRequestBody(), SecureCardDto.class);

                    byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
                    byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

                    String sql = "UPDATE secure_cards SET title = ?, holder_name = ?, encrypted_card_number = ?, encrypted_cvv = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, input.title());
                        stmt.setString(2, input.holderName());
                        stmt.setString(3, Base64.getEncoder().encodeToString(encNumberBytes));
                        stmt.setString(4, Base64.getEncoder().encodeToString(encCvvBytes));
                        stmt.setObject(5, cardId);
                        int updated = stmt.executeUpdate();

                        if (updated > 0) {
                            sendResponse(exchange, 200, "{\"status\": \"updated\"}");
                        } else {
                            sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                        }
                    }
                    return;
                }

                if ("DELETE".equalsIgnoreCase(method)) {
                    if (!"ROLE_ADMIN".equals(role)) {
                        sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                        return;
                    }

                    String sql = "DELETE FROM secure_cards WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setObject(1, cardId);
                        int deleted = stmt.executeUpdate();
                        if (deleted > 0) {
                            sendResponse(exchange, 200, "{\"status\": \"deleted\"}");
                        } else {
                            sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                        }
                    }
                    return;
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"Operation failed\"}");
            }
        });

        server.createContext("/api/v1/admin/users", exchange -> {
            if (handleCorsAndOptions(exchange)) return;
            String role = validateJwtAndGetRole(exchange);
            if (!"ROLE_ADMIN".equals(role)) {
                sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    Map<String, Object> req = mapper.readValue(exchange.getRequestBody(), Map.class);
                    String targetUser = (String) req.get("username");
                    String action = (String) req.get("action");

                    try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                        if ("block".equalsIgnoreCase(action)) {
                            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET is_blocked = ? WHERE username = ?")) {
                                stmt.setBoolean(1, (Boolean) req.get("block"));
                                stmt.setString(2, targetUser);
                                stmt.executeUpdate();
                            }
                        } else if ("change_role".equalsIgnoreCase(action)) {
                            try (PreparedStatement stmt = conn.prepareStatement("UPDATE users SET role = ? WHERE username = ?")) {
                                stmt.setString(1, (String) req.get("role"));
                                stmt.setString(2, targetUser);
                                stmt.executeUpdate();
                            }
                        } else if ("create".equalsIgnoreCase(action)) {
                            String salt = PasswordUtil.generateSalt();
                            String hashed = PasswordUtil.hashPassword((String) req.get("password"), salt);
                            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (username, password, salt, role) VALUES (?, ?, ?, ?)")) {
                                stmt.setString(1, targetUser);
                                stmt.setString(2, hashed);
                                stmt.setString(3, salt);
                                stmt.setString(4, (String) req.get("role"));
                                stmt.executeUpdate();
                            }
                        }
                    }
                    sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"User action processed\"}");
                } catch (Exception e) {
                    sendResponse(exchange, 400, "{\"error\": \"Invalid admin request structure\"}");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
    }

    private static boolean handleCorsAndOptions(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin != null ? origin : "http://127.0.0.1:3000");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Client-ID");
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static String validateJwtAndGetRole(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Missing or malformed token\"}");
            return null;
        }

        try {
            String token = authHeader.substring(7);
            DecodedJWT decodedJWT = JwtUtil.decodeJWT(token);
            return decodedJWT.getClaim("role").asString();
        } catch (Exception e) {
            sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Invalid token\"}");
            return null;
        }
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }
}
