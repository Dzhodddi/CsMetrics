package org.example.service.auth;

import org.example.dtos.auth.LoginDto;

public interface AuthService {

    String authenticateAndGetToken(LoginDto loginDto) throws Exception;
}
