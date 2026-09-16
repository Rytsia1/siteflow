package com.siteflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.mapper.BorrowRequestMapper;
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

    public ApprovalService(BorrowRequestMapper borrowRequestMapper) {
        this.borrowRequestMapper = borrowRequestMapper;
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
        BorrowRequest request = requirePendingRequest(requestId);

        // Transition: PENDING_APPROVAL → APPROVED
        borrowRequestMapper.updateApproval(requestId, ApprovalStatus.APPROVED, adminId, note);

        // Return a fresh read so the caller sees the persisted state
        return borrowRequestMapper.findById(requestId);
    }

    /**
     * Rejects a borrow request.
     *
     * <p>Transition: PENDING_APPROVAL → REJECTED
     *
     * <p>A rejection does not release any stock (stock was never decremented for a
     * pending-approval request in V2 flow). The note should explain the reason so
     * the requester can amend and resubmit if appropriate.
     *
     * @param requestId the borrow request to reject
     * @param adminId   the user id of the rejecting admin
     * @param note      reason for rejection — required so the requester can act on it
     * @return the updated BorrowRequest
     */
    @Transactional
    public BorrowRequest rejectBorrowRequest(Long requestId, Long adminId, String note) {
        BorrowRequest request = requirePendingRequest(requestId);

        // Transition: PENDING_APPROVAL → REJECTED
        borrowRequestMapper.updateApproval(requestId, ApprovalStatus.REJECTED, adminId, note);

        return borrowRequestMapper.findById(requestId);
    }

    /**
     * Lists borrow requests currently awaiting admin decision, for the
     * approval dashboard.
     */
    @Transactional(readOnly = true)
    public List<BorrowRequestView> listPendingBorrowRequests() {
        return borrowRequestMapper.findByApprovalStatusWithDetails(ApprovalStatus.PENDING_APPROVAL);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads the request and asserts it is still PENDING_APPROVAL.
     * Centralises the guard so both approve and reject share the same check.
     */
    private BorrowRequest requirePendingRequest(Long requestId) {
        BorrowRequest request = borrowRequestMapper.findById(requestId);
        if (request == null) {
            throw new IllegalArgumentException("Borrow request not found: " + requestId);
        }
        if (request.getApprovalStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Borrow request " + requestId + " is not pending approval (current status: "
                    + request.getApprovalStatus() + ")");
        }
        return request;
    }
}
