package org.example.dtos.card;

import java.util.UUID;

public record SecureCardResponseDto(
        UUID id,
        String title,
        String holderName,
        String cardNumber,
        String cvv
) {}
