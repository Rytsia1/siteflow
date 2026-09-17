package com.siteflow.service;

import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.Location;
import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.mapper.ItemMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.LocationMapper;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

@Service
public class InventoryService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> ALLOWED_SORT_COLUMNS = Map.of(
            "id", "i.id",
            "item_code", "i.item_code",
            "itemcode", "i.item_code",
            "name", "i.name",
            "category", "i.category",
            "unit", "i.unit",
            "min_stock_threshold", "i.min_stock_threshold",
            "minstockthreshold", "i.min_stock_threshold",
            "total_qty", "total_qty",
            "totalqty", "total_qty"
    );

    private final ItemMapper itemMapper;
    private final ItemStockMapper itemStockMapper;
    private final LocationMapper locationMapper;

    public InventoryService(ItemMapper itemMapper, ItemStockMapper itemStockMapper, LocationMapper locationMapper) {
        this.itemMapper = itemMapper;
        this.itemStockMapper = itemStockMapper;
        this.locationMapper = locationMapper;
    }

    @Transactional(readOnly = true)
    public List<ItemSummaryView> listItems() {
        return itemMapper.findAllWithStock();
    }

    @Transactional(readOnly = true)
    public List<ItemSummaryView> listItems(Integer page, Integer size) {
        return listItems(page, size, null, null, null, null);
    }

    /**
     * Server-side paginated, filtered, and sorted item listing executed directly in MySQL.
     * Validates pagination parameters and strictly whitelists sort fields to prevent SQL injection.
     */
    @Transactional(readOnly = true)
    public List<ItemSummaryView> listItems(Integer page, Integer size, ItemCategory category,
                                          String search, String sortBy, String sortDir) {
        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : DEFAULT_PAGE_SIZE;

        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must not be negative.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1.");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must not exceed 100.");
        }

        String sortColumn = resolveSortColumn(sortBy);
        String sortDirection = (sortDir != null && sortDir.trim().equalsIgnoreCase("desc")) ? "DESC" : "ASC";

        int offset = pageIndex * pageSize;
        int limit = pageSize;

        String sanitizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        return itemMapper.findWithStockPaged(offset, limit, category, sanitizedSearch, sortColumn, sortDirection);
    }

    @Transactional(readOnly = true)
    public int countItems(ItemCategory category, String search) {
        String sanitizedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return itemMapper.countWithStock(category, sanitizedSearch);
    }

    private String resolveSortColumn(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "i.id";
        }
        String normalized = sortBy.trim().toLowerCase();
        String column = ALLOWED_SORT_COLUMNS.get(normalized);
        if (column == null) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }
        return column;
    }

    @Transactional(readOnly = true)
    public List<ItemStockView> getStockByItem(Long itemId) {
        return itemStockMapper.findByItemId(itemId);
    }

    @Cacheable("locations")
    @Transactional(readOnly = true)
    public List<Location> listLocations() {
        return locationMapper.findAll();
    }

    @CacheEvict(value = "locations", allEntries = true)
    public void evictLocationsCache() {
        // Invalidate in-process locations cache
    }
}
