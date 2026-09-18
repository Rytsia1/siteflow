package com.siteflow.web.dto;

import java.util.List;

/**
 * Standard paginated response envelope for audit trail queries.
 */
public record PagedAuditLogView(
        List<AuditLogView> items,
        int totalCount,
        int page,
        int size,
        int totalPages
) {}
