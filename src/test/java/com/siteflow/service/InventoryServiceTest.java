package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.Location;
import com.siteflow.domain.enums.ItemCategory;
import com.siteflow.mapper.ItemMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.LocationMapper;
import com.siteflow.web.dto.ItemStockView;
import com.siteflow.web.dto.ItemSummaryView;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ItemMapper itemMapper;
    @Mock
    private ItemStockMapper itemStockMapper;
    @Mock
    private LocationMapper locationMapper;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(itemMapper, itemStockMapper, locationMapper);
    }

    @Test
    @DisplayName("listItems returns item summaries with aggregated stock")
    void listItems_returnsAllItemsWithStock() {
        ItemSummaryView item1 = new ItemSummaryView(1L, "DRL-001", "Rotary Hammer Drill", ItemCategory.TOOL, "units", 5, 2);
        ItemSummaryView item2 = new ItemSummaryView(2L, "SAF-001", "Safety Helmet", ItemCategory.CONSUMABLE, "pieces", 50, 10);
        when(itemMapper.findAllWithStock()).thenReturn(List.of(item1, item2));

        List<ItemSummaryView> items = inventoryService.listItems();

        assertThat(items).hasSize(2).containsExactly(item1, item2);
        verify(itemMapper).findAllWithStock();
    }

    @Test
    @DisplayName("getStockByItem returns stock breakdown per location for given item")
    void getStockByItem_returnsStockPerLocation() {
        ItemStockView stockMain = new ItemStockView(10L, "Main Warehouse", 5);
        ItemStockView stockSite = new ItemStockView(20L, "Site A", 3);
        when(itemStockMapper.findByItemId(1L)).thenReturn(List.of(stockMain, stockSite));

        List<ItemStockView> stocks = inventoryService.getStockByItem(1L);

        assertThat(stocks).hasSize(2).containsExactly(stockMain, stockSite);
        verify(itemStockMapper).findByItemId(1L);
    }

    @Test
    @DisplayName("listLocations returns all configured warehouse and site locations")
    void listLocations_returnsAllLocations() {
        Location loc1 = Location.builder().id(1L).locationName("Central Depot").build();
        Location loc2 = Location.builder().id(2L).locationName("Site Bravo").build();
        when(locationMapper.findAll()).thenReturn(List.of(loc1, loc2));

        List<Location> locations = inventoryService.listLocations();

        assertThat(locations).hasSize(2).containsExactly(loc1, loc2);
        verify(locationMapper).findAll();
    }
}
