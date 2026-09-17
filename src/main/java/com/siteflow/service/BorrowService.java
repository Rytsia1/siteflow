package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.TransactionType;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;

@Service
public class BorrowService {

    private final BorrowRequestMapper borrowRequestMapper;
    private final BorrowItemMapper borrowItemMapper;
    private final ItemStockMapper itemStockMapper;
    private final TransactionLogMapper transactionLogMapper;

    public BorrowService(BorrowRequestMapper borrowRequestMapper, BorrowItemMapper borrowItemMapper,
            ItemStockMapper itemStockMapper, TransactionLogMapper transactionLogMapper) {
        this.borrowRequestMapper = borrowRequestMapper;
        this.borrowItemMapper = borrowItemMapper;
        this.itemStockMapper = itemStockMapper;
        this.transactionLogMapper = transactionLogMapper;
    }

    public record BorrowItemRequest(Long itemId, int qty) {
    }

    public record ReturnLine(Long borrowItemId, int qty) {
    }

    /**
     * Creates a borrow request and allocates stock for each requested item in one transaction:
     * stock availability is checked up front, then each item is recorded, stock is decremented,
     * and a BORROW transaction log is written. Any failure rolls back the whole request.
     */
    @Transactional
    public BorrowRequest createBorrowRequest(Long userId, Long locationId, List<BorrowItemRequest> items) {
        for (BorrowItemRequest request : items) {
            if (request.qty() <= 0) {
                throw new IllegalArgumentException(
                        "Requested quantity must be positive for item " + request.itemId());
            }
            ItemStock stock = itemStockMapper.findByItemIdAndLocationId(request.itemId(), locationId);
            if (stock == null || stock.getCurrentQty() < request.qty()) {
                throw new IllegalStateException(
                        "Insufficient stock for item " + request.itemId() + " at location " + locationId);
            }
        }

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .userId(userId)
                .locationId(locationId)
                .requestDate(LocalDateTime.now())
                .status(BorrowStatus.BORROWED)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        borrowRequestMapper.insert(borrowRequest);

        for (BorrowItemRequest request : items) {
            BorrowItem borrowItem = BorrowItem.builder()
                    .borrowRequestId(borrowRequest.getId())
                    .itemId(request.itemId())
                    .qtyBorrowed(request.qty())
                    .qtyReturned(0)
                    .build();
            borrowItemMapper.insert(borrowItem);

            ItemStock stock = itemStockMapper.findByItemIdAndLocationId(request.itemId(), locationId);
            if (itemStockMapper.adjustQty(stock.getId(), -request.qty()) == 0) {
                throw new IllegalStateException(
                        "Insufficient stock for item " + request.itemId() + " at location " + locationId);
            }

            transactionLogMapper.insert(TransactionLog.builder()
                    .itemId(request.itemId())
                    .locationId(locationId)
                    .userId(userId)
                    .transactionType(TransactionType.BORROW)
                    .qtyChange(-request.qty())
                    .referenceId(borrowRequest.getId())
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        return borrowRequest;
    }

    /**
     * Records a return against a borrowed item, restores the returned quantity to stock,
     * logs a RETURN transaction, and updates the parent request's status to COMPLETED or
     * PARTIAL_RETURN depending on whether every item in the request has now been fully returned.
     * All steps run in one transaction so a partial failure leaves no partial state.
     */
    @Transactional
    public void processReturn(Long borrowItemId, int qtyReturned, Long userId) {
        BorrowItem borrowItem = borrowItemMapper.findById(borrowItemId);
        if (borrowItem == null) {
            throw new ResourceNotFoundException("Borrow item not found: " + borrowItemId);
        }
        if (qtyReturned <= 0) {
            throw new IllegalArgumentException("Return quantity must be positive for borrow item " + borrowItemId);
        }

        LocalDateTime now = LocalDateTime.now();
        // Atomically guarded: matches zero rows if this would push qty_returned past qty_borrowed,
        // whether because the line was already fully returned or another request raced this one.
        if (borrowItemMapper.recordReturn(borrowItemId, qtyReturned, now) == 0) {
            throw new IllegalStateException(
                    "Return quantity " + qtyReturned + " for borrow item " + borrowItemId
                    + " exceeds the outstanding balance.");
        }

        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowItem.getBorrowRequestId());
        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(borrowItem.getItemId(),
                borrowRequest.getLocationId());
        itemStockMapper.adjustQty(stock.getId(), qtyReturned);

        transactionLogMapper.insert(TransactionLog.builder()
                .itemId(borrowItem.getItemId())
                .locationId(borrowRequest.getLocationId())
                .userId(userId)
                .transactionType(TransactionType.RETURN)
                .qtyChange(qtyReturned)
                .referenceId(borrowRequest.getId())
                .timestamp(now)
                .build());

        List<BorrowItem> allItems = borrowItemMapper.findByBorrowRequestId(borrowRequest.getId());
        boolean allReturned = allItems.stream()
                .allMatch(item -> item.getQtyReturned() != null && item.getQtyReturned().equals(item.getQtyBorrowed()));
        borrowRequestMapper.updateStatus(borrowRequest.getId(),
                allReturned ? BorrowStatus.COMPLETED : BorrowStatus.PARTIAL_RETURN);
    }

    /**
     * Processes every return line against a single borrow request in one transaction. Every
     * line is verified to belong to the request before any of them are applied, so a bad line
     * rejects the whole batch instead of leaving stock partially restored.
     */
    @Transactional
    public void processReturnsForRequest(Long borrowRequestId, List<ReturnLine> lines, Long userId) {
        for (ReturnLine line : lines) {
            BorrowItem borrowItem = borrowItemMapper.findById(line.borrowItemId());
            if (borrowItem == null || !borrowItem.getBorrowRequestId().equals(borrowRequestId)) {
                throw new IllegalArgumentException(
                        "Borrow item " + line.borrowItemId() + " does not belong to request " + borrowRequestId);
            }
        }

        for (ReturnLine line : lines) {
            processReturn(line.borrowItemId(), line.qty(), userId);
        }
    }
}
