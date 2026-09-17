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
import com.siteflow.web.dto.ApproveRequestDto;
import com.siteflow.web.dto.BorrowRequestView;
import com.siteflow.web.dto.RejectRequestDto;

import jakarta.validation.Valid;

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
    public ApiResponse<List<BorrowRequestView>> listPending(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size,
            jakarta.servlet.http.HttpServletResponse response) {
        if (page == null && size == null) {
            return ApiResponse.success("Pending borrow requests retrieved.", approvalService.listPendingBorrowRequests());
        }
        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : 20;
        List<BorrowRequestView> requests = approvalService.listPendingBorrowRequests(pageIndex, pageSize);
        int total = approvalService.countPendingBorrowRequests();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (response != null) {
            response.setHeader("X-Total-Count", String.valueOf(total));
            response.setHeader("X-Page-Number", String.valueOf(pageIndex));
            response.setHeader("X-Page-Size", String.valueOf(pageSize));
            response.setHeader("X-Total-Pages", String.valueOf(totalPages));
        }
        return ApiResponse.success("Pending borrow requests retrieved.", requests);
    }

    /**
     * Approves a borrow request.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BorrowRequest> approveBorrowRequest(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        BorrowRequest approved = approvalService.approveBorrowRequest(
                id, principal.getUserId(), ApproveRequestDto.noteOf(dto));
        return ApiResponse.success("Borrow request approved.", approved);
    }

    /**
     * Rejects a borrow request, requiring an explanation note.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BorrowRequest> rejectBorrowRequest(
            @PathVariable Long id,
            @RequestBody @Valid RejectRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        BorrowRequest rejected = approvalService.rejectBorrowRequest(id, principal.getUserId(), dto.note());
        return ApiResponse.success("Borrow request rejected.", rejected);
    }
}
