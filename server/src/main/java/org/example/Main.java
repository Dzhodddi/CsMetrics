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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import javax.crypto.SecretKey;

import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.config.DatabaseConfig;
import org.example.dtos.LoginDto;
import org.example.dtos.MetricDto;
import org.example.dtos.SecureCardDto;
import org.example.dtos.TokenResponse;
import org.example.metrics.MetricsRegistry;
import org.example.proxies.ProxyFactory;
import org.example.reporters.DbMetricsReporter;
import org.example.reporters.HttpMetricsReporter;
import org.example.reporters.MetricsReporter;
import org.example.tcp.TcpMetricClient;
import org.example.utility.JwtUtil;
import org.example.cryptography.RsaUtil;
import org.example.cryptography.AesUtil;
import org.example.cryptography.SessionKeyStore;
import org.example.service.DataService;
import org.example.service.DefaultDataService;
import org.example.service.AuthService;
import org.example.service.CardService;
import org.example.service.ICardService;
import org.example.service.UserService;
import org.example.service.IUserService;

public class Main {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final KeyPair rsaKeyPair;
    private static final SessionKeyStore sessionKeyStore = new SessionKeyStore();
    private static final ConcurrentLinkedQueue<MetricDto> metricsStorage = new ConcurrentLinkedQueue<>();
    private static final SecretKey dbEncryptionKey = AesUtil.getSecretKeyFromBytes(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
    );

    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 9090;

    static {
        try {
            rsaKeyPair = RsaUtil.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize server RSA keys", e);
        }
    }

    public static void main(String[] args) throws Exception {
        DatabaseConfig.getDataSource();
        var metricClient = new TcpMetricClient(SERVER_HOST, SERVER_PORT);
        DefaultDataService realDataService = new DefaultDataService();
        CardService realCardService = new CardService(dbEncryptionKey);
        UserService realUserService = new UserService();

        MetricsRegistry registry = new MetricsRegistry(metricClient);
        Map<Class<? extends Annotation>, MetricsReporter> reporters = Map.of(
                DbQueryTimer.class, new DbMetricsReporter(registry),
                HttpRequestTimer.class, new HttpMetricsReporter(registry)
        );


        DataService proxyDataService = ProxyFactory.createProxy(realDataService, DataService.class, reporters);
        ICardService cardService = ProxyFactory.createProxy(realCardService, ICardService.class, reporters);
        IUserService userService = ProxyFactory.createProxy(realUserService, IUserService.class, reporters);
        AuthService authService = new AuthService();

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

        server.createContext("/api/v1/login", exchange -> {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin != null ? origin : "http://127.0.0.1:3000");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Client-ID, Authorization");
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

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

                    JsonNode rootNode = mapper.readTree(exchange.getRequestBody());
                    if (!rootNode.has("ciphertext")) {
                        sendResponse(exchange, 400, "{\"error\": \"Missing ciphertext payload\"}");
                        return;
                    }

                    byte[] encryptedBytes = Base64.getDecoder().decode(rootNode.get("ciphertext").asText());
                    String decryptedJson = new String(AesUtil.decrypt(encryptedBytes, aesKey), StandardCharsets.UTF_8);
                    LoginDto loginDto = mapper.readValue(decryptedJson, LoginDto.class);

                    try {
                        String token = authService.authenticateAndGetToken(loginDto);
                        String rawResponse = mapper.writeValueAsString(new TokenResponse(token));
                        byte[] encryptedResponseBytes = AesUtil.encrypt(rawResponse.getBytes(StandardCharsets.UTF_8), aesKey);
                        String encryptedResponseBase64 = Base64.getEncoder().encodeToString(encryptedResponseBytes);
                        sendResponse(exchange, 200, "{\"ciphertext\": \"" + encryptedResponseBase64 + "\"}");
                    } catch (Exception e) {
                        sendResponse(exchange, 401, "{\"error\": \"" + e.getMessage() + "\"}");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 400, "{\"error\": \"Bad request format or decryption error\"}");
                }
            }
        });

        server.createContext("/api/v1/validate", exchange -> {
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
            String role = validateJwtAndGetRole(exchange);
            if (role == null) return;

            String method = exchange.getRequestMethod();
            try {
                if ("POST".equalsIgnoreCase(method)) {
                    SecureCardDto input = mapper.readValue(exchange.getRequestBody(), SecureCardDto.class);
                    UUID newId = cardService.createCard(input);
                    sendResponse(exchange, 201, "{\"status\":\"created\", \"id\":\"" + newId + "\"}");
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    List<SecureCardDto> cards = cardService.getCards(role);
                    sendResponse(exchange, 200, mapper.writeValueAsString(cards));
                    return;
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"error\": \"Bad request or error: " + e.getMessage() + "\"}");
            }
        });

        server.createContext("/api/v1/cards/detail", exchange -> {
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

            try {
                if ("PUT".equalsIgnoreCase(method)) {
                    SecureCardDto input = mapper.readValue(exchange.getRequestBody(), SecureCardDto.class);
                    boolean updated = cardService.updateCard(cardId, input);
                    if (updated) {
                        sendResponse(exchange, 200, "{\"status\": \"updated\"}");
                    } else {
                        sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                    }
                    return;
                }

                if ("DELETE".equalsIgnoreCase(method)) {
                    if (!"ROLE_ADMIN".equals(role)) {
                        sendResponse(exchange, 403, "{\"error\": \"Forbidden\"}");
                        return;
                    }
                    boolean deleted = cardService.deleteCard(cardId);
                    if (deleted) {
                        sendResponse(exchange, 200, "{\"status\": \"deleted\"}");
                    } else {
                        sendResponse(exchange, 404, "{\"error\": \"Card not found\"}");
                    }
                    return;
                }
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\": \"Operation failed\"}");
            }
        });

        server.createContext("/api/v1/admin/users", exchange -> {
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

                    if ("block".equalsIgnoreCase(action)) {
                        userService.blockUser(targetUser, (Boolean) req.get("block"));
                    } else if ("change_role".equalsIgnoreCase(action)) {
                        userService.changeRole(targetUser, (String) req.get("role"));
                    } else if ("create".equalsIgnoreCase(action)) {
                        userService.createUser(targetUser, (String) req.get("password"), (String) req.get("role"));
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
