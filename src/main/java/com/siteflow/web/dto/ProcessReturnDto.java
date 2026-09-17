package com.siteflow.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProcessReturnDto(
        @NotEmpty @Size(max = 100, message = "Cannot exceed 100 items per request") @Valid List<Line> items) {

    public record Line(
            @NotNull Long borrowItemId,
            @Min(1) int qty) {
    }
}
