package org.example.dtos;

import java.util.UUID;

public record SecureCardDto(
        UUID id,
        String title,
        String holderName,
        String cardNumber,
        String cvv
) {}
