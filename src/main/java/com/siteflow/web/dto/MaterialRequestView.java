package com.siteflow.web.dto;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.MaterialRequestStatus;

public record MaterialRequestView(
        Long id,
        String requesterName,
        String justification,
        LocalDateTime requestDate,
        MaterialRequestStatus status) {
}
