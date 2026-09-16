package com.siteflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.ItemInstance;
import com.siteflow.service.AssetTrackingService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.CheckoutAssetDto;
import com.siteflow.web.dto.ReturnAssetDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assets")
public class AssetTrackingController {

    private final AssetTrackingService assetTrackingService;

    public AssetTrackingController(AssetTrackingService assetTrackingService) {
        this.assetTrackingService = assetTrackingService;
    }

    /**
     * Locks a specific physical tool to an approved borrow request via serial number or QR code.
     * Accessible by admins and warehouse staff handling checkout scanners.
     */
    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ApiResponse<ItemInstance> checkout(
            @RequestBody @Valid CheckoutAssetDto dto) {
        ItemInstance instance = assetTrackingService.checkoutItemInstance(
                dto.getEffectiveIdentifier(), dto.borrowRequestId());
        return ApiResponse.success("Asset checked out successfully.", instance);
    }

    /**
     * Processes physical return and inspection of a tool instance, updating its condition state.
     * Accessible by admins and warehouse staff handling return scanners.
     */
    @PostMapping("/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ApiResponse<ItemInstance> processReturn(
            @RequestBody @Valid ReturnAssetDto dto) {
        ItemInstance instance = assetTrackingService.returnItemInstance(dto.serialNumber(), dto.toolCondition());
        return ApiResponse.success("Asset return processed successfully.", instance);
    }
}
