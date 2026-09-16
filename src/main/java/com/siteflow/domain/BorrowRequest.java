package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowRequest {
    private Long id;
    private Long userId;
    private Long locationId;
    private LocalDateTime requestDate;
    private BorrowStatus status;
    private ApprovalStatus approvalStatus;
    private Long approvedBy;
    private String approvalNote;
    private LocalDateTime updatedAt;
}

