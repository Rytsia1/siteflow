package com.siteflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.Location;
import com.siteflow.mapper.ItemMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.LocationMapper;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

@Service
public class InventoryService {

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
        return listItems(null, null);
    }

    @Transactional(readOnly = true)
    public List<ItemSummaryView> listItems(Integer page, Integer size) {
        List<ItemSummaryView> all = itemMapper.findAllWithStock();
        if (page == null && size == null) {
            return all;
        }

        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : 20;

        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must not be negative.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1.");
        }
        if (pageSize > 100) {
            throw new IllegalArgumentException("Page size must not exceed 100.");
        }

        int fromIndex = pageIndex * pageSize;
        if (fromIndex >= all.size()) {
            return List.of();
        }
        return all.subList(fromIndex, Math.min(fromIndex + pageSize, all.size()));
    }

    @Transactional(readOnly = true)
    public List<ItemStockView> getStockByItem(Long itemId) {
        return itemStockMapper.findByItemId(itemId);
    }

    @Transactional(readOnly = true)
    public List<Location> listLocations() {
        return locationMapper.findAll();
    }
}
