package com.siteflow.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.service.AuditService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.ResourceNotFoundException;
import com.siteflow.web.dto.AuditLogView;
import com.siteflow.web.dto.PagedAuditLogView;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Read-only administrative API for inspecting system audit trails and accountability ledgers.
 * Strictly role-protected (ADMIN only) and append-oriented: no modification or deletion
 * endpoints exist.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Lists audit log events with server-side pagination and dynamic filtering.
     * Enforces newest-first sorting and caps maximum page size at 100.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PagedAuditLogView> listAuditLogs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            HttpServletResponse response) {

        PagedAuditLogView result = auditService.listAuditLogsPaged(
                page, size, action, resourceType, resourceId, actor, status, startDate, endDate);

        if (response != null) {
            response.setHeader("X-Total-Count", String.valueOf(result.totalCount()));
            response.setHeader("X-Page-Number", String.valueOf(result.page()));
            response.setHeader("X-Page-Size", String.valueOf(result.size()));
            response.setHeader("X-Total-Pages", String.valueOf(result.totalPages()));
        }

        return ApiResponse.success("Audit logs retrieved.", result);
    }

    /**
     * Retrieves an individual audit log record by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AuditLogView> getAuditLogById(@PathVariable Long id) {
        AuditLogView view = auditService.getAuditLogById(id);
        if (view == null) {
            throw new ResourceNotFoundException("Audit record not found: " + id);
        }
        return ApiResponse.success("Audit record retrieved.", view);
    }
}
