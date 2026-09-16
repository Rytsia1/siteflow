package com.siteflow.domain;

import java.time.LocalDateTime;

import com.siteflow.domain.enums.MaterialRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialRequest {
    private Long id;
    private Long requestedBy;
    private LocalDateTime requestDate;
    private MaterialRequestStatus status;
    private String justification;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
