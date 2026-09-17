package com.siteflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.ItemStock;
import com.siteflow.domain.StockAdjustment;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.AdjustmentType;
import com.siteflow.domain.enums.TransactionType;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.StockAdjustmentMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;

/**
 * Records manual IN/OUT stock corrections: updates item_stocks atomically, then writes a
 * stock_adjustments row and a transaction_logs entry in the same transaction. Mirrors
 * BorrowService's pattern for mutating stock — reuses ItemStockMapper.adjustQty's atomic
 * compare-and-swap UPDATE (guarded by current_qty + delta >= 0) as the sole negative-stock
 * guard, rather than a separate application-level check.
 */
@Service
public class StockAdjustmentService {

    private final ItemStockMapper itemStockMapper;
    private final StockAdjustmentMapper stockAdjustmentMapper;
    private final TransactionLogMapper transactionLogMapper;

    public StockAdjustmentService(ItemStockMapper itemStockMapper, StockAdjustmentMapper stockAdjustmentMapper,
            TransactionLogMapper transactionLogMapper) {
        this.itemStockMapper = itemStockMapper;
        this.stockAdjustmentMapper = stockAdjustmentMapper;
        this.transactionLogMapper = transactionLogMapper;
    }

    /**
     * Applies a manual stock correction for one item at one location.
     *
     * <p>item_stocks is FK-backed to both items and locations, so a non-null lookup here
     * guarantees all three (item, location, stock record) exist — no separate existence
     * checks needed.
     *
     * <p>The atomic adjustQty UPDATE runs before either insert below, so a rejected
     * adjustment (would go negative) leaves zero rows written anywhere.
     *
     * @param itemId     the item being adjusted
     * @param locationId the location the stock record belongs to
     * @param type       IN increases stock, OUT decreases it
     * @param qty        always positive; direction comes from type
     * @param reason     optional free-text explanation
     * @param adjustedBy the user id recording this adjustment, for audit
     * @return the persisted StockAdjustment
     */
    @Transactional
    public StockAdjustment createAdjustment(Long itemId, Long locationId, AdjustmentType type, int qty,
            String reason, Long adjustedBy) {
        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(itemId, locationId);
        if (stock == null) {
            throw new ResourceNotFoundException(
                    "No stock record for item " + itemId + " at location " + locationId);
        }

        int qtyDelta = type == AdjustmentType.IN ? qty : -qty;
        if (itemStockMapper.adjustQty(stock.getId(), qtyDelta) == 0) {
            throw new IllegalStateException(
                    "Adjustment would leave negative stock for item " + itemId + " at location " + locationId);
        }

        LocalDateTime now = LocalDateTime.now();
        StockAdjustment adjustment = StockAdjustment.builder()
                .itemId(itemId)
                .locationId(locationId)
                .adjustedBy(adjustedBy)
                .adjustmentType(type)
                .qty(qty)
                .reason(reason)
                .createdAt(now)
                .build();
        stockAdjustmentMapper.insert(adjustment);

        transactionLogMapper.insert(TransactionLog.builder()
                .itemId(itemId)
                .locationId(locationId)
                .userId(adjustedBy)
                .transactionType(TransactionType.ADJUSTMENT)
                .qtyChange(qtyDelta)
                .referenceId(adjustment.getId())
                .timestamp(now)
                .build());

        return adjustment;
    }
}
