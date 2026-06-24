package org.example.dtos.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginDto(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password
) {
}
