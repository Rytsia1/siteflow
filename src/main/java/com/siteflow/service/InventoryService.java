package com.siteflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.Item;
import com.siteflow.mapper.ItemMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.web.dto.ItemStockView;

@Service
public class InventoryService {

    private final ItemMapper itemMapper;
    private final ItemStockMapper itemStockMapper;

    public InventoryService(ItemMapper itemMapper, ItemStockMapper itemStockMapper) {
        this.itemMapper = itemMapper;
        this.itemStockMapper = itemStockMapper;
    }

    @Transactional(readOnly = true)
    public List<Item> listItems() {
        return itemMapper.findAll();
    }

    @Transactional(readOnly = true)
    public List<ItemStockView> getStockByItem(Long itemId) {
        return itemStockMapper.findByItemId(itemId);
    }
}
