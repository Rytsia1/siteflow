package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.security.UserPrincipal;

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
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;

    @Autowired
    public BorrowService(BorrowRequestMapper borrowRequestMapper, BorrowItemMapper borrowItemMapper,
            ItemStockMapper itemStockMapper, TransactionLogMapper transactionLogMapper,
            IdempotencyService idempotencyService, AuditService auditService) {
        this.borrowRequestMapper = borrowRequestMapper;
        this.borrowItemMapper = borrowItemMapper;
        this.itemStockMapper = itemStockMapper;
        this.transactionLogMapper = transactionLogMapper;
        this.idempotencyService = idempotencyService;
        this.auditService = auditService;
    }

    public BorrowService(BorrowRequestMapper borrowRequestMapper, BorrowItemMapper borrowItemMapper,
            ItemStockMapper itemStockMapper, TransactionLogMapper transactionLogMapper,
            IdempotencyService idempotencyService) {
        this(borrowRequestMapper, borrowItemMapper, itemStockMapper, transactionLogMapper, idempotencyService, null);
    }

    public BorrowService(BorrowRequestMapper borrowRequestMapper, BorrowItemMapper borrowItemMapper,
            ItemStockMapper itemStockMapper, TransactionLogMapper transactionLogMapper) {
        this(borrowRequestMapper, borrowItemMapper, itemStockMapper, transactionLogMapper, null, null);
    }

    public record BorrowItemRequest(Long itemId, int qty) {
    }

    public record ReturnLine(Long borrowItemId, int qty) {
    }

    private ItemStock findStock(Long itemId, Long locationId) {
        ItemStock stock = itemStockMapper.findByItemIdAndLocationIdForUpdate(itemId, locationId);
        if (stock == null) {
            stock = itemStockMapper.findByItemIdAndLocationId(itemId, locationId);
        }
        return stock;
    }

    /**
     * Creates a borrow request and allocates stock for each requested item in one transaction:
     * stock availability is checked up front with pessimistic row locks, then each item is recorded,
     * stock is decremented, and a BORROW transaction log is written.
     * Duplicate submissions are rejected via database-backed idempotency.
     */
    @Transactional
    public BorrowRequest createBorrowRequest(Long userId, Long locationId, List<BorrowItemRequest> items) {
        return createBorrowRequest(userId, locationId, items, null);
    }

    @Transactional
    public BorrowRequest createBorrowRequest(Long userId, Long locationId, List<BorrowItemRequest> items, String idempotencyKey) {
        String key = null;
        if (idempotencyService != null) {
            key = (idempotencyKey != null && !idempotencyKey.isBlank())
                    ? "idemp:" + userId + ":" + idempotencyKey
                    : idempotencyService.buildFingerprint("borrow", userId, locationId + ":" + items);
            idempotencyService.acquireOrThrow(key, userId, "/api/borrow-requests");
        }

        try {
            for (BorrowItemRequest request : items) {
                if (request.qty() <= 0) {
                    throw new IllegalArgumentException(
                            "Requested quantity must be positive for item " + request.itemId());
                }
                ItemStock stock = findStock(request.itemId(), locationId);
                if (stock == null || stock.getCurrentQty() < request.qty()) {
                    throw new IllegalStateException(
                            "Insufficient stock for item " + request.itemId() + " at location " + locationId);
                }
            }

            BorrowRequest borrowRequest = BorrowRequest.builder()
                    .userId(userId)
                    .locationId(locationId)
                    .requestDate(LocalDateTime.now())
                    .status(BorrowStatus.PENDING)
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

                ItemStock stock = findStock(request.itemId(), locationId);
                int beforeQty = stock != null ? stock.getCurrentQty() : 0;
                if (itemStockMapper.reserveStock(stock.getId(), request.qty()) == 0) {
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
                        .action(com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_CREATED.name())
                        .resourceType("ITEM")
                        .resourceId(request.itemId())
                        .status("SUCCESS")
                        .beforeState("avail: " + beforeQty)
                        .afterState("avail: " + (beforeQty - request.qty()))
                        .details("Reserved for borrow request #" + borrowRequest.getId())
                        .build());
            }

            if (auditService != null) {
                auditService.recordBusinessEvent(
                        com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_CREATED,
                        "BORROW_REQUEST",
                        borrowRequest.getId(),
                        "SUCCESS",
                        null,
                        ApprovalStatus.PENDING_APPROVAL.name(),
                        "Borrow request created for " + items.size() + " item(s)");
            }

            if (idempotencyService != null) {
                idempotencyService.complete(key);
            }
            return borrowRequest;
        } catch (Exception e) {
            if (idempotencyService != null) {
                idempotencyService.release(key);
            }
            throw e;
        }
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

        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowItem.getBorrowRequestId());
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + borrowItem.getBorrowRequestId());
        }
        if (borrowRequest.getStatus() != BorrowStatus.BORROWED && borrowRequest.getStatus() != BorrowStatus.PARTIAL_RETURN) {
            throw new IllegalStateException("Cannot process return for request " + borrowRequest.getId()
                    + " with status: " + borrowRequest.getStatus() + ". Must be BORROWED or PARTIAL_RETURN.");
        }

        LocalDateTime now = LocalDateTime.now();
        // Atomically guarded: matches zero rows if this would push qty_returned past qty_borrowed,
        // whether because the line was already fully returned or another request raced this one.
        if (borrowItemMapper.recordReturn(borrowItemId, qtyReturned, now) == 0) {
            throw new IllegalStateException(
                    "Return quantity " + qtyReturned + " for borrow item " + borrowItemId
                    + " exceeds the outstanding balance.");
        }

        ItemStock stock = itemStockMapper.findByItemIdAndLocationId(borrowItem.getItemId(),
                borrowRequest.getLocationId());
        int beforeQty = stock != null ? stock.getCurrentQty() : 0;
        itemStockMapper.adjustQty(stock.getId(), qtyReturned);

        transactionLogMapper.insert(TransactionLog.builder()
                .itemId(borrowItem.getItemId())
                .locationId(borrowRequest.getLocationId())
                .userId(userId)
                .transactionType(TransactionType.RETURN)
                .qtyChange(qtyReturned)
                .referenceId(borrowRequest.getId())
                .timestamp(now)
                .action(com.siteflow.domain.enums.AuditEventType.ITEM_RETURNED.name())
                .resourceType("ITEM")
                .resourceId(borrowItem.getItemId())
                .status("SUCCESS")
                .beforeState("qty: " + beforeQty)
                .afterState("qty: " + (beforeQty + qtyReturned))
                .details("Returned " + qtyReturned + " unit(s) for borrow request #" + borrowRequest.getId())
                .build());

        List<BorrowItem> allItems = borrowItemMapper.findByBorrowRequestId(borrowRequest.getId());
        boolean allReturned = allItems.stream()
                .allMatch(item -> item.getQtyReturned() != null && item.getQtyReturned().equals(item.getQtyBorrowed()));
        BorrowStatus targetStatus = allReturned ? BorrowStatus.COMPLETED : BorrowStatus.PARTIAL_RETURN;
        if (!borrowRequest.getStatus().canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    "Cannot transition borrow request " + borrowRequest.getId()
                    + " from " + borrowRequest.getStatus() + " to " + targetStatus);
        }
        borrowRequestMapper.updateStatus(borrowRequest.getId(), targetStatus);
    }

    /**
     * Retrieves a borrow request by ID with resource-level authorization.
     * ADMIN and WAREHOUSE_STAFF can inspect any request; FIELD_STAFF can only view their own.
     */
    @Transactional(readOnly = true)
    public BorrowRequest getBorrowRequest(Long id) {
        BorrowRequest request = borrowRequestMapper.findById(id);
        if (request == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + id);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            boolean isStaffOrAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_WAREHOUSE_STAFF"));
            if (!isStaffOrAdmin) {
                Object principal = auth.getPrincipal();
                if (principal instanceof UserPrincipal userPrincipal) {
                    if (!userPrincipal.getUserId().equals(request.getUserId())) {
                        throw new AccessDeniedException("Access denied.");
                    }
                }
            }
        }
        return request;
    }

    /**
     * Lists all borrow requests created by the specified user.
     */
    @Transactional(readOnly = true)
    public List<BorrowRequest> listUserBorrowRequests(Long userId) {
        return borrowRequestMapper.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<BorrowRequest> listUserBorrowRequests(Long userId, Integer page, Integer size) {
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
        int offset = pageIndex * pageSize;
        return borrowRequestMapper.findByUserIdPaged(userId, offset, pageSize);
    }

    @Transactional(readOnly = true)
    public int countUserBorrowRequests(Long userId) {
        return borrowRequestMapper.countByUserId(userId);
    }

    /**
     * Processes every return line against a single borrow request in one transaction. Every
     * line is verified to belong to the request before any of them are applied, so a bad line
     * rejects the whole batch instead of leaving stock partially restored.
     * Enforces that non-staff callers can only return items for their own borrow request.
     */
    @Transactional
    public void processReturnsForRequest(Long borrowRequestId, List<ReturnLine> lines, Long userId) {
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + borrowRequestId);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isStaffOrAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_WAREHOUSE_STAFF"));

        if (!isStaffOrAdmin && borrowRequest.getUserId() != null && !borrowRequest.getUserId().equals(userId)) {
            throw new AccessDeniedException("Access denied.");
        }

        if (borrowRequest.getStatus().isTerminal()) {
            throw new IllegalStateException(
                    "Cannot process returns for borrow request " + borrowRequestId + " in terminal status: " + borrowRequest.getStatus());
        }
        if (borrowRequest.getStatus() != BorrowStatus.BORROWED && borrowRequest.getStatus() != BorrowStatus.PARTIAL_RETURN) {
            throw new IllegalStateException("Cannot process return for request " + borrowRequestId
                    + " with status: " + borrowRequest.getStatus() + ". Must be BORROWED or PARTIAL_RETURN.");
        }

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

    /**
     * Cancels a pending borrow request.
     * Allowed only for the request owner or ADMIN, and only when the request is still PENDING / PENDING_APPROVAL.
     * Restores stock decremented during request creation.
     */
    @Transactional
    public BorrowRequest cancelBorrowRequest(Long borrowRequestId, Long userId) {
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + borrowRequestId);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && (borrowRequest.getUserId() == null || !borrowRequest.getUserId().equals(userId))) {
            throw new AccessDeniedException("Access denied.");
        }

        if (borrowRequest.getStatus() != BorrowStatus.PENDING
                || borrowRequest.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot cancel borrow request " + borrowRequestId
                    + " with status: " + borrowRequest.getStatus()
                    + " and approval status: " + borrowRequest.getApprovalStatus());
        }

        // Release reserved stock for all items in the request
        List<BorrowItem> items = borrowItemMapper.findByBorrowRequestId(borrowRequestId);
        LocalDateTime now = LocalDateTime.now();
        for (BorrowItem item : items) {
            ItemStock stock = itemStockMapper.findByItemIdAndLocationId(item.getItemId(), borrowRequest.getLocationId());
            if (stock != null) {
                itemStockMapper.releaseReservation(stock.getId(), item.getQtyBorrowed());
            }
            transactionLogMapper.insert(TransactionLog.builder()
                    .itemId(item.getItemId())
                    .locationId(borrowRequest.getLocationId())
                    .userId(userId)
                    .transactionType(TransactionType.RETURN)
                    .qtyChange(item.getQtyBorrowed())
                    .referenceId(borrowRequestId)
                    .timestamp(now)
                    .action(com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_CANCELLED.name())
                    .resourceType("ITEM")
                    .resourceId(item.getItemId())
                    .status("SUCCESS")
                    .details("Reservation released for cancelled borrow request #" + borrowRequestId)
                    .build());
        }

        borrowRequestMapper.updateApproval(
                borrowRequestId, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.REJECTED, userId, "Cancelled by requester");

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_CANCELLED,
                    "BORROW_REQUEST",
                    borrowRequestId,
                    "REJECTED",
                    ApprovalStatus.PENDING_APPROVAL.name(),
                    ApprovalStatus.REJECTED.name(),
                    "Cancelled by requester");
        }
        return borrowRequestMapper.findById(borrowRequestId);
    }

    /**
     * Checks out / dispenses an approved borrow request.
     * Transitions request status from PENDING to BORROWED and fulfills the stock reservations.
     * Allowed for ADMIN and WAREHOUSE_STAFF.
     */
    @Transactional
    public BorrowRequest checkoutBorrowRequest(Long borrowRequestId, Long staffUserId) {
        BorrowRequest borrowRequest = borrowRequestMapper.findById(borrowRequestId);
        if (borrowRequest == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + borrowRequestId);
        }

        if (borrowRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException("Borrow request " + borrowRequestId + " is not approved (approval status: "
                    + borrowRequest.getApprovalStatus() + "). Cannot check out.");
        }

        if (borrowRequest.getStatus() != BorrowStatus.PENDING) {
            throw new IllegalStateException("Borrow request " + borrowRequestId + " cannot be checked out with status: "
                    + borrowRequest.getStatus() + ". Must be PENDING.");
        }

        int updated = borrowRequestMapper.updateStatusGuarded(borrowRequestId, BorrowStatus.PENDING, BorrowStatus.BORROWED);
        if (updated == 0) {
            throw new IllegalStateException("Borrow request " + borrowRequestId + " was concurrently updated.");
        }

        List<BorrowItem> items = borrowItemMapper.findByBorrowRequestId(borrowRequestId);
        LocalDateTime now = LocalDateTime.now();
        if (items != null) {
            for (BorrowItem item : items) {
                ItemStock stock = itemStockMapper.findByItemIdAndLocationId(item.getItemId(), borrowRequest.getLocationId());
                if (stock != null) {
                    itemStockMapper.fulfillReservation(stock.getId(), item.getQtyBorrowed());
                }
                if (transactionLogMapper != null && borrowRequest.getLocationId() != null) {
                    transactionLogMapper.insert(TransactionLog.builder()
                            .itemId(item.getItemId())
                            .locationId(borrowRequest.getLocationId())
                            .userId(staffUserId)
                            .transactionType(TransactionType.BORROW)
                            .qtyChange(-item.getQtyBorrowed())
                            .referenceId(borrowRequestId)
                            .timestamp(now)
                            .action(com.siteflow.domain.enums.AuditEventType.ITEM_BORROWED.name())
                            .resourceType("ITEM")
                            .resourceId(item.getItemId())
                            .status("SUCCESS")
                            .details("Dispensed for approved borrow request #" + borrowRequestId)
                            .build());
                }
            }
        }

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.ITEM_BORROWED,
                    "BORROW_REQUEST",
                    borrowRequestId,
                    "SUCCESS",
                    BorrowStatus.PENDING.name(),
                    BorrowStatus.BORROWED.name(),
                    "Borrow request checked out / dispensed");
        }

        return borrowRequestMapper.findById(borrowRequestId);
    }
}
