package com.siteflow.web.dto;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.ApprovalStatus;
import com.siteflow.domain.enums.BorrowStatus;

public record BorrowRequestView(
        Long id,
        String requesterName,
        String locationName,
        LocalDateTime requestDate,
        BorrowStatus status,
        ApprovalStatus approvalStatus) {
}
