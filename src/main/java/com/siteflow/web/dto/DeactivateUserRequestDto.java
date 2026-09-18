package com.siteflow.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record DeactivateUserRequestDto(
        @NotNull(message = "Confirmation must be explicitly provided.")
        @AssertTrue(message = "You must confirm the deactivation and data anonymization request.")
        Boolean confirm,
        String reason) {
}
