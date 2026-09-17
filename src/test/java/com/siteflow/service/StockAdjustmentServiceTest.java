package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.ItemStock;
import com.siteflow.domain.StockAdjustment;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.StockAdjustmentMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {

    @Mock
    private ItemStockMapper itemStockMapper;
    @Mock
    private StockAdjustmentMapper stockAdjustmentMapper;
    @Mock
    private TransactionLogMapper transactionLogMapper;

    private StockAdjustmentService stockAdjustmentService;

    @BeforeEach
    void setUp() {
        stockAdjustmentService = new StockAdjustmentService(itemStockMapper, stockAdjustmentMapper,
                transactionLogMapper);
    }

    @Test
    @DisplayName("IN adjustment increments stock and records both audit rows with a positive qtyChange")
    void createAdjustment_in_incrementsStockAndLogs() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(5).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(itemStockMapper.adjustQty(50L, 3)).thenReturn(1);

        StockAdjustment result = stockAdjustmentService.createAdjustment(1L, 10L, AdjustmentType.IN, 3, "restock",
                7L);

        assertThat(result.getAdjustmentType()).isEqualTo(AdjustmentType.IN);
        assertThat(result.getQty()).isEqualTo(3);
        verify(itemStockMapper).adjustQty(50L, 3);
        verify(stockAdjustmentMapper).insert(any());
        verify(transactionLogMapper).insert(any());
    }

    @Test
    @DisplayName("OUT adjustment decrements stock via a negative delta")
    void createAdjustment_out_decrementsStock() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(5).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(itemStockMapper.adjustQty(50L, -3)).thenReturn(1);

        StockAdjustment result = stockAdjustmentService.createAdjustment(1L, 10L, AdjustmentType.OUT, 3, "damaged",
                7L);

        assertThat(result.getAdjustmentType()).isEqualTo(AdjustmentType.OUT);
        verify(itemStockMapper).adjustQty(50L, -3);
        verify(stockAdjustmentMapper).insert(any());
        verify(transactionLogMapper).insert(any());
    }

    @Test
    @DisplayName("OUT adjustment that would go negative fails with a 409-mapped exception, writing nothing")
    void createAdjustment_wouldGoNegative_throwsWithoutSideEffects() {
        ItemStock stock = ItemStock.builder().id(50L).itemId(1L).locationId(10L).currentQty(2).build();
        when(itemStockMapper.findByItemIdAndLocationId(1L, 10L)).thenReturn(stock);
        when(itemStockMapper.adjustQty(50L, -3)).thenReturn(0);

        assertThatThrownBy(() -> stockAdjustmentService.createAdjustment(1L, 10L, AdjustmentType.OUT, 3, null, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative stock");

        verify(stockAdjustmentMapper, never()).insert(any());
        verify(transactionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Adjustment rejects a non-positive quantity without touching stock or writing any audit row")
    void createAdjustment_nonPositiveQty_throwsWithoutSideEffects() {
        assertThatThrownBy(() -> stockAdjustmentService.createAdjustment(1L, 10L, AdjustmentType.IN, 0, null, 7L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(itemStockMapper, never()).findByItemIdAndLocationId(any(), any());
        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
        verify(stockAdjustmentMapper, never()).insert(any());
        verify(transactionLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Adjustment fails with 404-mapped exception when no stock record exists for the item/location")
    void createAdjustment_unknownItemOrLocation_throwsResourceNotFound() {
        when(itemStockMapper.findByItemIdAndLocationId(1L, 99L)).thenReturn(null);

        assertThatThrownBy(() -> stockAdjustmentService.createAdjustment(1L, 99L, AdjustmentType.IN, 1, null, 7L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(itemStockMapper, never()).adjustQty(any(), anyInt());
        verify(stockAdjustmentMapper, never()).insert(any());
        verify(transactionLogMapper, never()).insert(any());
    }
}
