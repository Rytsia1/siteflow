package com.siteflow.web.dto;

import com.siteflow.domain.enums.ToolCondition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReturnAssetDto(
        @NotBlank String serialNumber,
        @NotNull ToolCondition toolCondition) {
}
