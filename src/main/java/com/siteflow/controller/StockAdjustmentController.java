package com.siteflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.StockAdjustment;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.StockAdjustmentService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.CreateStockAdjustmentDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;

    public StockAdjustmentController(StockAdjustmentService stockAdjustmentService) {
        this.stockAdjustmentService = stockAdjustmentService;
    }

    /**
     * Records a manual IN/OUT stock correction for an item at a location.
     * Accessible only by users with ADMIN or WAREHOUSE_STAFF role.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ResponseEntity<ApiResponse<StockAdjustment>> createAdjustment(
            @RequestBody @Valid CreateStockAdjustmentDto dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        StockAdjustment created = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? stockAdjustmentService.createAdjustment(
                        dto.itemId(), dto.locationId(), dto.adjustmentType(), dto.qty(), dto.reason(),
                        principal.getUserId(), idempotencyKey)
                : stockAdjustmentService.createAdjustment(
                        dto.itemId(), dto.locationId(), dto.adjustmentType(), dto.qty(), dto.reason(),
                        principal.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock adjustment recorded.", created));
    }
}
