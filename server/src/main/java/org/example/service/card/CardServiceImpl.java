package org.example.service.card;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.annotations.DbQueryTimer;
import org.example.config.DatabaseConfig;
import org.example.utility.AesUtil;
import org.example.dtos.card.SecureCardRequestDto;
import org.example.dtos.card.SecureCardResponseDto;

public class CardServiceImpl implements CardService {

    private final SecretKey dbEncryptionKey;

    public CardServiceImpl(SecretKey dbEncryptionKey) {
        this.dbEncryptionKey = dbEncryptionKey;
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "INSERT_CARD")
    public UUID createCard(SecureCardRequestDto input) throws Exception {
        String fingerprint = validateCardInput(input);
        checkUniqueCard(fingerprint, null);

        UUID uniqueId = UUID.randomUUID();
        byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
        byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

        String encNumberBase64 = Base64.getEncoder().encodeToString(encNumberBytes);
        String encCvvBase64 = Base64.getEncoder().encodeToString(encCvvBytes);

        String sql = "INSERT INTO secure_cards (id, title, holder_name, encrypted_card_number, encrypted_cvv, card_fingerprint) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, uniqueId);
            stmt.setString(2, input.title());
            stmt.setString(3, input.holderName());
            stmt.setString(4, encNumberBase64);
            stmt.setString(5, encCvvBase64);
            stmt.setString(6, fingerprint);
            stmt.executeUpdate();
        }
        return uniqueId;
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "SELECT_CARDS")
    public List<SecureCardResponseDto> getCards(String role) throws Exception {
        String sql = "SELECT id, title, holder_name, encrypted_card_number, encrypted_cvv FROM secure_cards";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<SecureCardResponseDto> cards = new ArrayList<>();
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
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

                cards.add(new SecureCardResponseDto(id, title, holderName, cardNumber, cvv));
            }
            return cards;
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "UPDATE_CARD")
    public boolean updateCard(UUID cardId, SecureCardRequestDto input) throws Exception {
        if ("[FORBIDDEN]".equals(input.cardNumber())) {
            throw new SecurityException("Cannot update card with masked values");
        }
        String fingerprint = validateCardInput(input);
        checkUniqueCard(fingerprint, cardId);

        byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
        byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

        String sql = "UPDATE secure_cards SET title = ?, holder_name = ?, encrypted_card_number = ?, encrypted_cvv = ?, card_fingerprint = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, input.title());
            stmt.setString(2, input.holderName());
            stmt.setString(3, Base64.getEncoder().encodeToString(encNumberBytes));
            stmt.setString(4, Base64.getEncoder().encodeToString(encCvvBytes));
            stmt.setString(5, fingerprint);
            stmt.setObject(6, cardId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "DELETE_CARD")
    public boolean deleteCard(UUID cardId) throws Exception {
        String sql = "DELETE FROM secure_cards WHERE id = ?";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, cardId);
            return stmt.executeUpdate() > 0;
        }
    }

    private String validateCardInput(SecureCardRequestDto input) {
        if (input.cardNumber() == null || !input.cardNumber().matches("^[0-9]{16}$")) {
            throw new IllegalArgumentException("Card number must be exactly 16 digits");
        }
        if (input.cvv() == null || !input.cvv().matches("^[0-9]{3}$")) {
            throw new IllegalArgumentException("CVV must be exactly 3 digits");
        }
        if (input.title() == null || input.title().trim().isEmpty() || input.title().length() > 50) {
            throw new IllegalArgumentException("Title is required and must not exceed 50 characters");
        }
        if (input.holderName() == null || input.holderName().trim().isEmpty() || input.holderName().length() > 50) {
            throw new IllegalArgumentException("Holder name is required and must not exceed 50 characters");
        }

        return DigestUtils.sha256Hex(input.cardNumber());
    }

    private void checkUniqueCard(String fingerprint, UUID currentId) throws Exception {
        String sql = "SELECT COUNT(*) FROM secure_cards WHERE card_fingerprint = ?" +
                (currentId != null ? " AND id != ?" : "");
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fingerprint);
            if (currentId != null) {
                stmt.setObject(2, currentId);
            }
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                throw new IllegalArgumentException("Enter unique card number");
            }
        }
    }
}
