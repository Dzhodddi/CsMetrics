package org.example.service;

import java.util.List;
import java.util.UUID;
import org.example.dtos.SecureCardDto;

public interface ICardService {
    UUID createCard(SecureCardDto input) throws Exception;
    List<SecureCardDto> getCards(String role) throws Exception;
    boolean updateCard(UUID cardId, SecureCardDto input) throws Exception;
    boolean deleteCard(UUID cardId) throws Exception;
}
