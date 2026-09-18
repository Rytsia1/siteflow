package com.siteflow.web.dto;

import java.time.LocalDateTime;

/**
 * Immutable view model representing an individual audit trail and accountability record.
 * Protects internal implementation details while providing full contextual traceability.
 */
public record AuditLogView(
        Long id,
        LocalDateTime timestamp,
        String action,
        String resourceType,
        Long resourceId,
        String actorUsername,
        Long userId,
        String status,
        String beforeState,
        String afterState,
        String details,
        String ipAddress
) {}
