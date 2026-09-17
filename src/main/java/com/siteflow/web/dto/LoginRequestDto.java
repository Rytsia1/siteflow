package com.siteflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotBlank(message = "Username is required")
        @Size(max = 64, message = "Username must not exceed 64 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 128 characters")
        String password) {

    /**
     * Prevents accidental leakage of plain-text passwords in application logs or debug traces.
     */
    @Override
    public String toString() {
        return "LoginRequestDto[username=" + username + ", password=***]";
    }
}
