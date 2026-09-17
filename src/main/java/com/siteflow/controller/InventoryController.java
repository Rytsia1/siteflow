package com.siteflow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.service.InventoryService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

@RestController
@RequestMapping("/api/items")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF', 'FIELD_STAFF', 'PROCUREMENT')")
    public ApiResponse<List<ItemSummaryView>> listItems() {
        return ApiResponse.success("Items retrieved.", inventoryService.listItems());
    }

    @GetMapping("/{id}/stocks")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ApiResponse<List<ItemStockView>> getStockByItem(@PathVariable Long id) {
        return ApiResponse.success("Stock levels retrieved.", inventoryService.getStockByItem(id));
    }
}
