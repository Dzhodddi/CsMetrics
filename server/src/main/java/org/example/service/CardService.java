package org.example.service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.config.DatabaseConfig;
import org.example.cryptography.AesUtil;
import org.example.dtos.SecureCardDto;

public class CardService implements ICardService {

    private final SecretKey dbEncryptionKey;

    public CardService(SecretKey dbEncryptionKey) {
        this.dbEncryptionKey = dbEncryptionKey;
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/cards (POST)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "INSERT_CARD")
    public UUID createCard(SecureCardDto input) throws Exception {
        UUID newId = UUID.randomUUID();
        byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
        byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

        String encNumberBase64 = Base64.getEncoder().encodeToString(encNumberBytes);
        String encCvvBase64 = Base64.getEncoder().encodeToString(encCvvBytes);

        String sql = "INSERT INTO secure_cards (id, title, holder_name, encrypted_card_number, encrypted_cvv) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, newId);
            stmt.setString(2, input.title());
            stmt.setString(3, input.holderName());
            stmt.setString(4, encNumberBase64);
            stmt.setString(5, encCvvBase64);
            stmt.executeUpdate();
        }
        return newId;
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/cards (GET)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "SELECT_CARD")
    public List<SecureCardDto> getCards(String role) throws Exception {
        String sql = "SELECT id, title, holder_name, encrypted_card_number, encrypted_cvv FROM secure_cards";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<SecureCardDto> cards = new ArrayList<>();
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

                cards.add(new SecureCardDto(id, title, holderName, cardNumber, cvv));
            }
            return cards;
        }
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/cards/detail (PUT)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "UPDATE_CARD")
    public boolean updateCard(UUID cardId, SecureCardDto input) throws Exception {
        if ("[FORBIDDEN]".equals(input.cardNumber())) {
            throw new SecurityException("Cannot update card with masked values");
        }
        byte[] encNumberBytes = AesUtil.encrypt(input.cardNumber().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);
        byte[] encCvvBytes = AesUtil.encrypt(input.cvv().getBytes(StandardCharsets.UTF_8), dbEncryptionKey);

        String sql = "UPDATE secure_cards SET title = ?, holder_name = ?, encrypted_card_number = ?, encrypted_cvv = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, input.title());
            stmt.setString(2, input.holderName());
            stmt.setString(3, Base64.getEncoder().encodeToString(encNumberBytes));
            stmt.setString(4, Base64.getEncoder().encodeToString(encCvvBytes));
            stmt.setObject(5, cardId);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    @HttpRequestTimer(path = "/api/v1/cards/detail (DELETE)")
    @DbQueryTimer(dbName = "PostgreSQL", queryAction = "DELETE_CARD")
    public boolean deleteCard(UUID cardId) throws Exception {
        String sql = "DELETE FROM secure_cards WHERE id = ?";
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, cardId);
            return stmt.executeUpdate() > 0;
        }
    }
}
