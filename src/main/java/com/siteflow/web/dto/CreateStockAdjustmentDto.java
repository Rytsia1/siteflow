package com.siteflow.web.dto;

import com.siteflow.domain.enums.AdjustmentType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateStockAdjustmentDto(
        @NotNull Long itemId,
        @NotNull Long locationId,
        @NotNull AdjustmentType adjustmentType,
        @Min(1) int qty,
        String reason) {
}
