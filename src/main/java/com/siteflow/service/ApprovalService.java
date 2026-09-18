package com.siteflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.mapper.BorrowRequestMapper;
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

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalService(BorrowRequestMapper borrowRequestMapper, AuditService auditService) {
        this.borrowRequestMapper = borrowRequestMapper;
        this.auditService = auditService;
    }

    public ApprovalService(BorrowRequestMapper borrowRequestMapper) {
        this(borrowRequestMapper, null);
    }

    /**
     * Approves a borrow request.
     *
     * <p>Transition: PENDING_APPROVAL → APPROVED
     *
     * <p>After approval the request is considered active and the warehouse staff
     * may proceed with dispensing the items. The adminId and optional note are
     * persisted for audit purposes.
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
     * <p>Stock was already decremented when the request was created (borrowing happens
     * up front; approval is a downstream sign-off), and rejection does not restore it —
     * the physical items are still out and must come back through the normal return
     * flow regardless of the approval outcome. The note should explain the reason so
     * the requester can amend and resubmit if appropriate.
     *
     * @param requestId the borrow request to reject
     * @param adminId   the user id of the rejecting admin
     * @param note      reason for rejection — required so the requester can act on it
     * @return the updated BorrowRequest
     */
    @Transactional
    public BorrowRequest rejectBorrowRequest(Long requestId, Long adminId, String note) {
        BorrowRequest request = transitionApproval(requestId, adminId, note, ApprovalStatus.REJECTED);
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
