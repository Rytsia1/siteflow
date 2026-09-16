package com.siteflow.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record MaterialRequestDto(
        String justification,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long itemId,
            Integer qty,
            Integer requestedQty) {

        public int getQuantity() {
            if (requestedQty != null && requestedQty > 0) {
                return requestedQty;
            }
            if (qty != null && qty > 0) {
                return qty;
            }
            return 0;
        }
    }
}
