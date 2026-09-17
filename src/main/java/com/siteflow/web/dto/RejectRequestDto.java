package com.siteflow.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequestDto(@NotBlank String note) {
}
