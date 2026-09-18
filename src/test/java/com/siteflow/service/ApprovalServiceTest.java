package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;
import com.siteflow.mapper.BorrowRequestMapper;
import com.siteflow.domain.BorrowItem;
import com.siteflow.domain.ItemStock;
import com.siteflow.domain.TransactionLog;
import com.siteflow.mapper.BorrowItemMapper;
import com.siteflow.mapper.ItemStockMapper;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.web.ResourceNotFoundException;
import com.siteflow.web.dto.BorrowRequestView;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private BorrowRequestMapper borrowRequestMapper;
    @Mock
    private BorrowItemMapper borrowItemMapper;
    @Mock
    private ItemStockMapper itemStockMapper;
    @Mock
    private TransactionLogMapper transactionLogMapper;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(borrowRequestMapper, null, borrowItemMapper, itemStockMapper, transactionLogMapper);
    }

    @Test
    @DisplayName("approveBorrowRequest succeeds when the request is pending approval")
    void approve_pendingRequest_succeeds() {
        BorrowRequest pending = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .status(BorrowStatus.PENDING).build();
        BorrowRequest approved = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.APPROVED)
                .status(BorrowStatus.PENDING).build();
        when(borrowRequestMapper.findById(1L)).thenReturn(pending, approved);
        when(borrowRequestMapper.updateApproval(1L, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.APPROVED, 9L,
                "ok")).thenReturn(1);

        BorrowRequest result = approvalService.approveBorrowRequest(1L, 9L, "ok");

        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(borrowRequestMapper).updateApproval(1L, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.APPROVED, 9L, "ok");
    }

    @Test
    @DisplayName("rejectBorrowRequest succeeds when the request is pending approval and releases reserved stock")
    void reject_pendingRequest_succeeds() {
        BorrowRequest pending = BorrowRequest.builder().id(1L).locationId(10L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .status(BorrowStatus.PENDING).build();
        BorrowRequest rejected = BorrowRequest.builder().id(1L).locationId(10L).approvalStatus(ApprovalStatus.REJECTED)
                .approvalNote("Insufficient justification").status(BorrowStatus.PENDING).build();
        BorrowItem item = BorrowItem.builder().id(101L).borrowRequestId(1L).itemId(5L).qtyBorrowed(2).build();
        ItemStock stock = ItemStock.builder().id(50L).itemId(5L).locationId(10L).currentQty(3).reservedQty(2).build();

        when(borrowRequestMapper.findById(1L)).thenReturn(pending, rejected);
        when(borrowRequestMapper.updateApproval(1L, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.REJECTED, 9L,
                "Insufficient justification")).thenReturn(1);
        when(borrowItemMapper.findByBorrowRequestId(1L)).thenReturn(List.of(item));
        when(itemStockMapper.findByItemIdAndLocationId(5L, 10L)).thenReturn(stock);
        when(itemStockMapper.releaseReservation(50L, 2)).thenReturn(1);

        BorrowRequest result = approvalService.rejectBorrowRequest(1L, 9L, "Insufficient justification");

        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verify(borrowRequestMapper).updateApproval(1L, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.REJECTED, 9L,
                "Insufficient justification");
        verify(itemStockMapper).releaseReservation(50L, 2);
        verify(transactionLogMapper).insert(any());
    }

    @Test
    @DisplayName("approveBorrowRequest fails with 404-mapped exception for an unknown request id")
    void approve_unknownRequest_throwsResourceNotFound() {
        when(borrowRequestMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(99L, 9L, "ok"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(borrowRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejectBorrowRequest fails with 404-mapped exception for an unknown request id")
    void reject_unknownRequest_throwsResourceNotFound() {
        when(borrowRequestMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> approvalService.rejectBorrowRequest(99L, 9L, "no"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(borrowRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveBorrowRequest fails when the request was already rejected (invalid transition)")
    void approve_alreadyRejected_throwsWithoutWriting() {
        BorrowRequest rejected = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.REJECTED).build();
        when(borrowRequestMapper.findById(1L)).thenReturn(rejected);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(1L, 9L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");

        verify(borrowRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("rejectBorrowRequest fails when the request was already approved (invalid transition)")
    void reject_alreadyApproved_throwsWithoutWriting() {
        BorrowRequest approved = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.APPROVED).build();
        when(borrowRequestMapper.findById(1L)).thenReturn(approved);

        assertThatThrownBy(() -> approvalService.rejectBorrowRequest(1L, 9L, "no"))
                .isInstanceOf(IllegalStateException.class);

        verify(borrowRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveBorrowRequest rejects modifying a borrow request that is already COMPLETED")
    void approve_completedRequest_throwsIllegalState() {
        BorrowRequest completed = BorrowRequest.builder()
                .id(1L)
                .approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .status(BorrowStatus.COMPLETED)
                .build();
        when(borrowRequestMapper.findById(1L)).thenReturn(completed);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(1L, 9L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");

        verify(borrowRequestMapper, never()).updateApproval(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approveBorrowRequest fails when a concurrent decision already actioned the request (repeated transition)")
    void approve_concurrentlyActioned_throwsIllegalState() {
        BorrowRequest pending = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .status(BorrowStatus.PENDING).build();
        when(borrowRequestMapper.findById(1L)).thenReturn(pending);
        when(borrowRequestMapper.updateApproval(eq(1L), eq(ApprovalStatus.PENDING_APPROVAL),
                eq(ApprovalStatus.APPROVED), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(1L, 9L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already actioned");
    }

    @Test
    @DisplayName("listPendingBorrowRequests returns list from mapper")
    void listPendingBorrowRequests_returnsPendingViews() {
        BorrowRequestView view = new BorrowRequestView(1L, "Alice", "Central Depot", LocalDateTime.now(),
                BorrowStatus.PENDING, ApprovalStatus.PENDING_APPROVAL);
        when(borrowRequestMapper.findByApprovalStatusWithDetails(ApprovalStatus.PENDING_APPROVAL))
                .thenReturn(List.of(view));

        List<BorrowRequestView> result = approvalService.listPendingBorrowRequests();

        assertThat(result).hasSize(1).containsExactly(view);
    }
}
