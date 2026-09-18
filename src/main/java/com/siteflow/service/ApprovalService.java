package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.siteflow.web.dto.BorrowRequestView;

/**
 * Manages the approval workflow for borrow requests.
 *
 * State transitions handled here:
 *   PENDING_APPROVAL → APPROVED   (approveBorrowRequest)
 *   PENDING_APPROVAL → REJECTED   (rejectBorrowRequest)
 *
 * Only requests that are currently PENDING_APPROVAL may be actioned;
 * attempting to approve or reject a request in any other state throws
 * IllegalStateException to prevent double-processing.
 */
@Service
public class ApprovalService {

    private final BorrowRequestMapper borrowRequestMapper;
    private final AuditService auditService;
    private final BorrowItemMapper borrowItemMapper;
    private final ItemStockMapper itemStockMapper;
    private final TransactionLogMapper transactionLogMapper;

    @Autowired
    public ApprovalService(BorrowRequestMapper borrowRequestMapper, AuditService auditService,
                           BorrowItemMapper borrowItemMapper, ItemStockMapper itemStockMapper,
                           TransactionLogMapper transactionLogMapper) {
        this.borrowRequestMapper = borrowRequestMapper;
        this.auditService = auditService;
        this.borrowItemMapper = borrowItemMapper;
        this.itemStockMapper = itemStockMapper;
        this.transactionLogMapper = transactionLogMapper;
    }

    public ApprovalService(BorrowRequestMapper borrowRequestMapper, AuditService auditService) {
        this(borrowRequestMapper, auditService, null, null, null);
    }

    public ApprovalService(BorrowRequestMapper borrowRequestMapper) {
        this(borrowRequestMapper, null, null, null, null);
    }

    /**
     * Approves a borrow request.
     *
     * <p>Transition: PENDING_APPROVAL → APPROVED
     *
     * <p>After approval the request is considered active and the warehouse staff
     * may proceed with dispensing the items. The reservation remains held.
     *
     * @param requestId the borrow request to approve
     * @param adminId   the user id of the approving admin
     * @param note      optional approval message visible to the requester
     * @return the updated BorrowRequest
     */
    @Transactional
    public BorrowRequest approveBorrowRequest(Long requestId, Long adminId, String note) {
        BorrowRequest request = transitionApproval(requestId, adminId, note, ApprovalStatus.APPROVED);
        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_APPROVED,
                    "BORROW_REQUEST",
                    requestId,
                    "SUCCESS",
                    ApprovalStatus.PENDING_APPROVAL.name(),
                    ApprovalStatus.APPROVED.name(),
                    note != null ? note : "Approved by administrator");
        }
        return request;
    }

    /**
     * Rejects a borrow request.
     *
     * <p>Transition: PENDING_APPROVAL → REJECTED
     *
     * <p>Releases all reserved stock back to available inventory and records an audit log.
     * Physical items never left the warehouse; availability is completely restored.
     *
     * @param requestId the borrow request to reject
     * @param adminId   the user id of the rejecting admin
     * @param note      reason for rejection — required so the requester can act on it
     * @return the updated BorrowRequest
     */
    @Transactional
    public BorrowRequest rejectBorrowRequest(Long requestId, Long adminId, String note) {
        BorrowRequest request = transitionApproval(requestId, adminId, note, ApprovalStatus.REJECTED);

        // Release reserved stock for all items in the request
        if (borrowItemMapper != null && itemStockMapper != null) {
            List<BorrowItem> items = borrowItemMapper.findByBorrowRequestId(requestId);
            if (items != null) {
                LocalDateTime now = LocalDateTime.now();
                for (BorrowItem item : items) {
                    ItemStock stock = itemStockMapper.findByItemIdAndLocationId(item.getItemId(), request.getLocationId());
                    if (stock != null) {
                        itemStockMapper.releaseReservation(stock.getId(), item.getQtyBorrowed());
                    }
                    if (transactionLogMapper != null && request.getLocationId() != null) {
                        transactionLogMapper.insert(TransactionLog.builder()
                                .itemId(item.getItemId())
                                .locationId(request.getLocationId())
                                .userId(adminId)
                                .transactionType(TransactionType.RETURN)
                                .qtyChange(item.getQtyBorrowed())
                                .referenceId(requestId)
                                .timestamp(now)
                                .action(com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_REJECTED.name())
                                .resourceType("ITEM")
                                .resourceId(item.getItemId())
                                .status("SUCCESS")
                                .details("Reservation released for rejected borrow request #" + requestId + ": " + (note != null ? note : ""))
                                .build());
                    }
                }
            }
        }

        if (auditService != null) {
            auditService.recordBusinessEvent(
                    com.siteflow.domain.enums.AuditEventType.BORROW_REQUEST_REJECTED,
                    "BORROW_REQUEST",
                    requestId,
                    "REJECTED",
                    ApprovalStatus.PENDING_APPROVAL.name(),
                    ApprovalStatus.REJECTED.name(),
                    note);
        }
        return request;
    }

    /**
     * Lists borrow requests currently awaiting admin decision, for the
     * approval dashboard.
     */
    @Transactional(readOnly = true)
    public List<BorrowRequestView> listPendingBorrowRequests() {
        return borrowRequestMapper.findByApprovalStatusWithDetails(ApprovalStatus.PENDING_APPROVAL);
    }

    @Transactional(readOnly = true)
    public List<BorrowRequestView> listPendingBorrowRequests(Integer page, Integer size) {
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
        return borrowRequestMapper.findByApprovalStatusWithDetailsPaged(ApprovalStatus.PENDING_APPROVAL, offset, pageSize);
    }

    @Transactional(readOnly = true)
    public int countPendingBorrowRequests() {
        return borrowRequestMapper.countByApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Shared PENDING_APPROVAL → {approved|rejected} transition used by both
     * approveBorrowRequest and rejectBorrowRequest, since the only difference
     * between the two is which target status is written.
     *
     * <p>The pre-check gives a precise "already actioned" message for the common
     * case; the atomic conditional UPDATE (matched by affected-row count) is what
     * actually prevents two concurrent decisions on the same request from both
     * succeeding, in case the request changed between the check and the write.
     */
    private BorrowRequest transitionApproval(Long requestId, Long adminId, String note, ApprovalStatus newStatus) {
        BorrowRequest request = requirePendingRequest(requestId);
        if (!request.getApprovalStatus().canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition approval status from " + request.getApprovalStatus() + " to " + newStatus);
        }
        if (request.getStatus() == BorrowStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot modify borrow request " + requestId + " that is already completed.");
        }

        int updated = borrowRequestMapper.updateApproval(
                requestId, ApprovalStatus.PENDING_APPROVAL, newStatus, adminId, note);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Borrow request " + requestId + " was already actioned by another request "
                    + "and is no longer pending approval.");
        }

        return borrowRequestMapper.findById(requestId);
    }

    /**
     * Loads the request and asserts it is still PENDING_APPROVAL.
     * Centralises the guard so both approve and reject share the same check.
     */
    private BorrowRequest requirePendingRequest(Long requestId) {
        BorrowRequest request = borrowRequestMapper.findById(requestId);
        if (request == null) {
            throw new ResourceNotFoundException("Borrow request not found: " + requestId);
        }
        if (request.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Borrow request " + requestId + " is not pending approval (current status: "
                    + request.getApprovalStatus() + ")");
        }
        return request;
    }
}
