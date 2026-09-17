package com.siteflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.security.UserPrincipal;
import com.siteflow.service.BorrowService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.CreateBorrowRequestDto;
import com.siteflow.web.dto.ProcessReturnDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/borrow-requests")
public class BorrowController {

    private final BorrowService borrowService;

    public BorrowController(BorrowService borrowService) {
        this.borrowService = borrowService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF')")
    public ResponseEntity<ApiResponse<BorrowRequest>> createBorrowRequest(
            @RequestBody @Valid CreateBorrowRequestDto dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<BorrowService.BorrowItemRequest> items = dto.items().stream()
                .map(line -> new BorrowService.BorrowItemRequest(line.itemId(), line.qty()))
                .toList();
        BorrowRequest created = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? borrowService.createBorrowRequest(principal.getUserId(), dto.locationId(), items, idempotencyKey)
                : borrowService.createBorrowRequest(principal.getUserId(), dto.locationId(), items);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Borrow request created.", created));
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF', 'FIELD_STAFF')")
    public ApiResponse<Void> processReturns(
            @PathVariable Long id,
            @RequestBody @Valid ProcessReturnDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<BorrowService.ReturnLine> lines = dto.items().stream()
                .map(line -> new BorrowService.ReturnLine(line.borrowItemId(), line.qty()))
                .toList();
        borrowService.processReturnsForRequest(id, lines, principal.getUserId());
        return ApiResponse.success("Return processed.", null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WAREHOUSE_STAFF', 'FIELD_STAFF')")
    public ApiResponse<BorrowRequest> getBorrowRequest(@PathVariable Long id) {
        return ApiResponse.success("Borrow request retrieved.", borrowService.getBorrowRequest(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF')")
    public ApiResponse<List<BorrowRequest>> listMyBorrowRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Borrow requests retrieved.", borrowService.listUserBorrowRequests(principal.getUserId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'FIELD_STAFF')")
    public ApiResponse<BorrowRequest> cancelBorrowRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        BorrowRequest cancelled = borrowService.cancelBorrowRequest(id, principal.getUserId());
        return ApiResponse.success("Borrow request cancelled.", cancelled);
    }
}
