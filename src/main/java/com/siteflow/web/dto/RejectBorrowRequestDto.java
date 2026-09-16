package com.siteflow.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectBorrowRequestDto(@NotBlank String note) {
}
