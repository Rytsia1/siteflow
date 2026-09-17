package com.siteflow.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKeyRecord {
    private Long id;
    private String keyValue;
    private Long userId;
    private String endpoint;
    private String status;
    private LocalDateTime createdAt;
}
