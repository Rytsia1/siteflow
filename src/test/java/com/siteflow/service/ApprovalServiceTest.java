package com.siteflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.siteflow.domain.BorrowRequest;
import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.mapper.BorrowRequestMapper;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private BorrowRequestMapper borrowRequestMapper;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(borrowRequestMapper);
    }

    @Test
    @DisplayName("approveBorrowRequest succeeds when the request is pending approval")
    void approve_pendingRequest_succeeds() {
        BorrowRequest pending = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        BorrowRequest approved = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.APPROVED).build();
        when(borrowRequestMapper.findById(1L)).thenReturn(pending, approved);
        when(borrowRequestMapper.updateApproval(1L, ApprovalStatus.PENDING_APPROVAL, ApprovalStatus.APPROVED, 9L,
                "ok")).thenReturn(1);

        BorrowRequest result = approvalService.approveBorrowRequest(1L, 9L, "ok");

        assertThat(result.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("approveBorrowRequest fails for an unknown request id")
    void approve_unknownRequest_throwsIllegalArgument() {
        when(borrowRequestMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(99L, 9L, "ok"))
                .isInstanceOf(IllegalArgumentException.class);

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
    @DisplayName("approveBorrowRequest fails when a concurrent decision already actioned the request (repeated transition)")
    void approve_concurrentlyActioned_throwsIllegalState() {
        BorrowRequest pending = BorrowRequest.builder().id(1L).approvalStatus(ApprovalStatus.PENDING_APPROVAL)
                .build();
        when(borrowRequestMapper.findById(1L)).thenReturn(pending);
        when(borrowRequestMapper.updateApproval(eq(1L), eq(ApprovalStatus.PENDING_APPROVAL),
                eq(ApprovalStatus.APPROVED), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> approvalService.approveBorrowRequest(1L, 9L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already actioned");
    }
}
