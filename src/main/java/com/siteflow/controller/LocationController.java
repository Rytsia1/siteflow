package com.siteflow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.Location;
import com.siteflow.service.InventoryService;
import com.siteflow.web.ApiResponse;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final InventoryService inventoryService;

    public LocationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF', 'FIELD_STAFF')")
    public ApiResponse<List<Location>> listLocations() {
        return ApiResponse.success("Locations retrieved.", inventoryService.listLocations());
    }
}
