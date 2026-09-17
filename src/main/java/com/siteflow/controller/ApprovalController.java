package com.siteflow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.ApprovalService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.ApproveBorrowRequestDto;
import com.siteflow.web.dto.BorrowRequestView;
import com.siteflow.web.dto.RejectBorrowRequestDto;

@RestController
@RequestMapping("/api/approvals/borrow-requests")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * Lists borrow requests currently pending admin decision.
     * Accessible only by administrators.
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<BorrowRequestView>> listPending() {
        return ApiResponse.success("Pending borrow requests retrieved.", approvalService.listPendingBorrowRequests());
    }

    /**
     * Approves a borrow request.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BorrowRequest> approveBorrowRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveBorrowRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        String note = (dto != null) ? dto.note() : null;
        BorrowRequest approved = approvalService.approveBorrowRequest(id, principal.getUserId(), note);
        return ApiResponse.success("Borrow request approved.", approved);
    }

    /**
     * Rejects a borrow request, optionally accepting an explanation note.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BorrowRequest> rejectBorrowRequest(
            @PathVariable Long id,
            @RequestBody(required = false) RejectBorrowRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        String note = (dto != null) ? dto.note() : null;
        BorrowRequest rejected = approvalService.rejectBorrowRequest(id, principal.getUserId(), note);
        return ApiResponse.success("Borrow request rejected.", rejected);
    }
}
