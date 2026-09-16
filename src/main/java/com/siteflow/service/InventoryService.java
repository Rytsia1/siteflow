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
        return itemMapper.findAllWithStock();
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
