package org.example.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginDto(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password
) {
}
