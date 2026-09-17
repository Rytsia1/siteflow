package com.siteflow.web.dto;

public record AuthTokenView(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String username,
        String role) {
}
