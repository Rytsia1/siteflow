package com.siteflow.web.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record GeneratePoDto(
        @NotBlank String supplierName,
        LocalDateTime expectedDeliveryDate) {
}
