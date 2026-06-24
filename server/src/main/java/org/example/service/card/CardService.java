package org.example.service.card;

import java.util.List;
import java.util.UUID;
import org.example.dtos.card.SecureCardRequestDto;
import org.example.dtos.card.SecureCardResponseDto;

public interface CardService {
    UUID createCard(SecureCardRequestDto input) throws Exception;
    List<SecureCardResponseDto> getCards(String role) throws Exception;
    boolean updateCard(UUID cardId, SecureCardRequestDto input) throws Exception;
    boolean deleteCard(UUID cardId) throws Exception;
}
