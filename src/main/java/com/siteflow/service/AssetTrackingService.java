package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.ItemInstance;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.domain.enums.ToolCondition;
import com.siteflow.domain.enums.TransactionType;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.mapper.ItemInstanceMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;

/**
 * Manages the hardened lifecycle of individually-tracked physical tool instances.
 *
 * Checkout flow:
 *   Worker presents serial number or QR code -> validates tool exists, is in GOOD condition,
 *   is currently available (preventing duplicate borrow), and request is APPROVED ->
 *   atomically assigns instance via conditional UPDATE -> transitions request to BORROWED ->
 *   logs BORROW transaction.
 *
 * Return flow:
 *   Worker returns tool with observed condition -> validates tool is currently borrowed
 *   (preventing duplicate returns and returning unborrowed tools) -> atomically releases
 *   instance -> updates condition and availability -> updates borrow item returned count ->
 *   updates request status (COMPLETED / PARTIAL_RETURN) -> restores stock if GOOD ->
 *   logs RETURN transaction.
 */
@Service
public class AssetTrackingService {

    private final ItemInstanceMapper itemInstanceMapper;
    private final BorrowRequestMapper borrowRequestMapper;
    private final BorrowItemMapper borrowItemMapper;
    private final ItemStockMapper itemStockMapper;
    private final TransactionLogMapper transactionLogMapper;

    public AssetTrackingService(ItemInstanceMapper itemInstanceMapper,
                                BorrowRequestMapper borrowRequestMapper) {
        this(itemInstanceMapper, borrowRequestMapper, null, null, null);
    }

    @Autowired
    public AssetTrackingService(ItemInstanceMapper itemInstanceMapper,
                                BorrowRequestMapper borrowRequestMapper,
                                BorrowItemMapper borrowItemMapper,
                                ItemStockMapper itemStockMapper,
                                TransactionLogMapper transactionLogMapper) {
        this.itemInstanceMapper = itemInstanceMapper;
        this.borrowRequestMapper = borrowRequestMapper;
        this.borrowItemMapper = borrowItemMapper;
        this.itemStockMapper = itemStockMapper;
        this.transactionLogMapper = transactionLogMapper;
    }

    /**
     * Locks a specific physical tool instance to an approved borrow request.
     */
    @Transactional
    public ItemInstance checkoutItemInstance(String identifier, Long borrowRequestId) {
        return checkoutItemInstance(identifier, borrowRequestId, null);
    }

    /**
     * Locks a specific physical tool instance to an approved borrow request with audit user ID.
     */
    @Transactional
    public ItemInstance checkoutItemInstance(String identifier, Long borrowRequestId, Long userId) {
        ItemInstance instance = resolveInstance(identifier);

        // 1. Tool condition check: only tools in GOOD condition can be checked out
        if (instance.getToolCondition() != ToolCondition.GOOD) {
            throw new IllegalStateException(
                    "Tool " + identifier + " cannot be checked out — current condition: "
                    + instance.getToolCondition());
        }

        // 2. Tool availability check: prevent duplicate borrow on an already borrowed instance
        if (Boolean.FALSE.equals(instance.getIsAvailable()) || instance.getCurrentBorrowRequestId() != null) {
            throw new IllegalStateException(
                    "Tool " + identifier + " is already borrowed by active request "
                    + instance.getCurrentBorrowRequestId());
        }

        // 3. Parent borrow request check
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + borrowRequestId);
        }

        // Validate request approval status
        if (borrowRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Borrow request " + borrowRequestId + " is not approved (approval status: "
                    + borrowRequest.getApprovalStatus() + "). Tool checkout is not allowed.");
        }

        // Validate request lifecycle state
        if (borrowRequest.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Borrow request " + borrowRequestId + " is already completed (terminal state "
                    + borrowRequest.getStatus() + "). Tool checkout is not allowed.");
        }
        if (borrowRequest.getStatus() != BorrowStatus.PENDING && borrowRequest.getStatus() != BorrowStatus.BORROWED) {
            throw new IllegalStateException(
                    "Borrow request " + borrowRequestId + " cannot check out tools with status: "
                    + borrowRequest.getStatus() + ". Must be PENDING or BORROWED.");
        }

        // 4. Validate that this tool item belongs to the borrow request
        if (borrowItemMapper != null) {
            List<BorrowItem> requestItems = borrowItemMapper.findByBorrowRequestId(borrowRequestId);
            if (requestItems != null && !requestItems.isEmpty()) {
                boolean itemMatches = requestItems.stream()
                        .anyMatch(item -> item.getItemId().equals(instance.getItemId()));
                if (!itemMatches) {
                    throw new IllegalStateException(
                            "Tool " + identifier + " (item ID " + instance.getItemId()
                            + ") is not part of borrow request " + borrowRequestId);
                }
            }
        }

        // 5. Concurrency-safe atomic assignment: lock the instance to the borrow request
        int assigned = itemInstanceMapper.assignToBorrowRequest(instance.getId(), borrowRequestId);
        if (assigned == 0) {
            throw new IllegalStateException(
                    "Tool " + identifier + " could not be checked out — it is already checked out or unavailable.");
        }

        // 6. Transition request status to BORROWED if it was still PENDING
        if (borrowRequest.getStatus() == BorrowStatus.PENDING) {
            if (!borrowRequest.getStatus().canTransitionTo(BorrowStatus.BORROWED)) {
                throw new IllegalStateException(
                        "Cannot transition borrow request " + borrowRequestId + " from "
                        + borrowRequest.getStatus() + " to BORROWED");
            }
            borrowRequestMapper.updateStatus(borrowRequestId, BorrowStatus.BORROWED);
        }

        // 7. Audit logging
        if (transactionLogMapper != null && borrowRequest.getLocationId() != null) {
            transactionLogMapper.insert(TransactionLog.builder()
                    .itemId(instance.getItemId())
                    .locationId(borrowRequest.getLocationId())
                    .userId(userId != null ? userId : borrowRequest.getUserId())
                    .transactionType(TransactionType.BORROW)
                    .qtyChange(-1)
                    .referenceId(borrowRequestId)
                    .timestamp(LocalDateTime.now())
                    .action(com.siteflow.domain.enums.AuditEventType.ITEM_BORROWED.name())
                    .resourceType("ITEM_INSTANCE")
                    .resourceId(instance.getId())
                    .status("SUCCESS")
                    .beforeState("AVAILABLE (SN: " + instance.getSerialNumber() + ")")
                    .afterState("BORROWED")
                    .details("Tool instance " + instance.getSerialNumber() + " checked out for borrow request #" + borrowRequestId)
                    .build());
        }

        return itemInstanceMapper.findById(instance.getId());
    }

    /**
     * Processes physical return of a tool instance and updates its condition state.
     */
    @Transactional
    public ItemInstance returnItemInstance(String identifier, ToolCondition returnedCondition) {
        return returnItemInstance(identifier, returnedCondition, null);
    }

    /**
     * Processes physical return of a tool instance with audit user ID.
     */
    @Transactional
    public ItemInstance returnItemInstance(String identifier, ToolCondition returnedCondition, Long userId) {
        ItemInstance instance = resolveInstance(identifier);

        // 1. Validate tool is currently borrowed: prevent duplicate return or returning unborrowed tool
        if (instance.getCurrentBorrowRequestId() == null || Boolean.TRUE.equals(instance.getIsAvailable())) {
            throw new IllegalStateException("Tool " + identifier + " is not currently borrowed.");
        }

        Long borrowRequestId = instance.getCurrentBorrowRequestId();
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Associated borrow request not found: " + borrowRequestId);
        }

        if (borrowRequest.getStatus() != BorrowStatus.BORROWED && borrowRequest.getStatus() != BorrowStatus.PARTIAL_RETURN) {
            throw new IllegalStateException(
                    "Cannot process return for request " + borrowRequestId + " with status: "
                    + borrowRequest.getStatus() + ". Must be BORROWED or PARTIAL_RETURN.");
        }

        // 2. Determine post-return availability:
        // GOOD -> tool is immediately available for subsequent checkout.
        // NEEDS_REPAIR / BROKEN -> tool is quarantined (is_available = FALSE).
        boolean isAvailable = (returnedCondition == ToolCondition.GOOD);

        // 3. Atomically release tool instance
        int released = itemInstanceMapper.releaseReturn(instance.getId(), returnedCondition, isAvailable);
        if (released == 0) {
            throw new IllegalStateException("Tool " + identifier + " return failed — tool is not currently borrowed.");
        }

        // 4. Update BorrowItem return count and BorrowRequest status
        if (borrowItemMapper != null) {
            List<BorrowItem> requestItems = borrowItemMapper.findByBorrowRequestId(borrowRequestId);
            if (requestItems != null) {
                // Find matching line for this item
                BorrowItem targetLine = requestItems.stream()
                        .filter(item -> item.getItemId().equals(instance.getItemId()))
                        .findFirst()
                        .orElse(null);

                if (targetLine != null) {
                    borrowItemMapper.recordReturn(targetLine.getId(), 1, LocalDateTime.now());
                }

                // Check if all lines are now fully returned
                List<BorrowItem> updatedItems = borrowItemMapper.findByBorrowRequestId(borrowRequestId);
                boolean allReturned = updatedItems != null && !updatedItems.isEmpty() && updatedItems.stream()
                        .allMatch(item -> item.getQtyReturned() != null && item.getQtyReturned().equals(item.getQtyBorrowed()));

                BorrowStatus targetStatus = allReturned ? BorrowStatus.COMPLETED : BorrowStatus.PARTIAL_RETURN;
                if (!borrowRequest.getStatus().canTransitionTo(targetStatus)) {
                    throw new IllegalStateException(
                            "Cannot transition borrow request " + borrowRequestId + " from "
                            + borrowRequest.getStatus() + " to " + targetStatus);
                }
                borrowRequestMapper.updateStatus(borrowRequestId, targetStatus);
            }
        }

        // 5. Restore stock if returned in GOOD condition
        if (returnedCondition == ToolCondition.GOOD && itemStockMapper != null && borrowRequest.getLocationId() != null) {
            ItemStock stock = itemStockMapper.findByItemIdAndLocationId(instance.getItemId(), borrowRequest.getLocationId());
            if (stock != null) {
                itemStockMapper.adjustQty(stock.getId(), 1);
            }
        }

        // 6. Audit logging
        if (transactionLogMapper != null && borrowRequest.getLocationId() != null) {
            transactionLogMapper.insert(TransactionLog.builder()
                    .itemId(instance.getItemId())
                    .locationId(borrowRequest.getLocationId())
                    .userId(userId != null ? userId : borrowRequest.getUserId())
                    .transactionType(TransactionType.RETURN)
                    .qtyChange(1)
                    .referenceId(borrowRequestId)
                    .timestamp(LocalDateTime.now())
                    .action(com.siteflow.domain.enums.AuditEventType.ITEM_RETURNED.name())
                    .resourceType("ITEM_INSTANCE")
                    .resourceId(instance.getId())
                    .status("SUCCESS")
                    .beforeState("BORROWED")
                    .afterState("RETURNED (" + returnedCondition + ")")
                    .details("Tool instance " + instance.getSerialNumber() + " returned with condition " + returnedCondition)
                    .build());
        }

        return itemInstanceMapper.findById(instance.getId());
    }

    /**
     * Resolves a physical tool by serial number or QR code.
     */
    private ItemInstance resolveInstance(String identifier) {
        ItemInstance instance = itemInstanceMapper.findBySerialNumber(identifier);
        if (instance == null) {
            instance = itemInstanceMapper.findByQrCodeValue(identifier);
        }
        if (instance == null) {
            throw new ResourceNotFoundException("No tool found with identifier: " + identifier);
        }
        return instance;
    }
}
