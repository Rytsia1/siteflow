package com.siteflow.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CheckoutAssetDto(
        String serialNumber,
        String qrCodeValue,
        @NotNull Long borrowRequestId) {

    @AssertTrue(message = "Either serialNumber or qrCodeValue must be provided")
    public boolean isIdentifierProvided() {
        return getEffectiveIdentifier() != null;
    }

    public String getEffectiveIdentifier() {
        if (serialNumber != null && !serialNumber.isBlank()) {
            return serialNumber.trim();
        }
        if (qrCodeValue != null && !qrCodeValue.isBlank()) {
            return qrCodeValue.trim();
        }
        return null;
    }
}
