package org.example.dtos.card;

public record SecureCardRequestDto (
    String title,
    String holderName,
    String cardNumber,
    String cvv
) {}
