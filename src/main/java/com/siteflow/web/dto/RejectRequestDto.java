package com.siteflow.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequestDto(
        @NotBlank(message = "Rejection note is required")
        String note
) {
}
