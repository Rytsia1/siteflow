package com.siteflow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.service.InventoryService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/items")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF', 'FIELD_STAFF', 'PROCUREMENT')")
    public ApiResponse<List<ItemSummaryView>> listItems(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) ItemCategory category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            HttpServletResponse response) {
        if (page == null && size == null && category == null && search == null && sortBy == null && sortDir == null) {
            return ApiResponse.success("Items retrieved.", inventoryService.listItems());
        }

        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : InventoryService.DEFAULT_PAGE_SIZE;

        List<ItemSummaryView> items = inventoryService.listItems(pageIndex, pageSize, category, search, sortBy, sortDir);
        int totalElements = inventoryService.countItems(category, search);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        if (response != null) {
            response.setHeader("X-Total-Count", String.valueOf(totalElements));
            response.setHeader("X-Page-Number", String.valueOf(pageIndex));
            response.setHeader("X-Page-Size", String.valueOf(pageSize));
            response.setHeader("X-Total-Pages", String.valueOf(totalPages));
        }

        return ApiResponse.success("Items retrieved.", items);
    }

    @GetMapping("/{id}/stocks")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF')")
    public ApiResponse<List<ItemStockView>> getStockByItem(@PathVariable Long id) {
        return ApiResponse.success("Stock levels retrieved.", inventoryService.getStockByItem(id));
    }
}
