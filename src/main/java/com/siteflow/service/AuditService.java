package com.siteflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.siteflow.domain.TransactionLog;
import com.siteflow.domain.enums.AuditEventType;
import com.siteflow.mapper.TransactionLogMapper;
import com.siteflow.security.UserPrincipal;
import com.siteflow.security.ratelimit.RateLimitProperties;
import com.siteflow.security.ratelimit.RateLimitingFilter;
import com.siteflow.web.dto.AuditLogView;
import com.siteflow.web.dto.PagedAuditLogView;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized service for recording and retrieving audit trail and accountability events.
 *
 * Guarantees:
 * 1. Actor identity is resolved strictly from the authenticated Spring Security context,
 *    preventing client-side audit spoofing.
 * 2. Unauthenticated, automated, or background tasks are clearly attributed to "SYSTEM".
 * 3. Sensitive credentials, passwords, hashes, and tokens are strictly excluded from audit logs.
 * 4. Business event recording participates in caller database transactions for transactional consistency.
 */
@Slf4j
@Service
public class AuditService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final TransactionLogMapper transactionLogMapper;
    private final RateLimitProperties rateLimitProperties;

    @Autowired
    public AuditService(TransactionLogMapper transactionLogMapper, RateLimitProperties rateLimitProperties) {
        this.transactionLogMapper = transactionLogMapper;
        this.rateLimitProperties = rateLimitProperties;
    }

    public AuditService(TransactionLogMapper transactionLogMapper) {
        this(transactionLogMapper, null);
    }

    /**
     * Records a business state-changing audit event. Participates in the active transaction.
     *
     * @param action       the typed audit action
     * @param resourceType the domain entity type (e.g. "BORROW_REQUEST", "ITEM", "MATERIAL_REQUEST")
     * @param resourceId   the entity ID
     * @param status       outcome status ("SUCCESS", "REJECTED", "FAILURE")
     * @param beforeState  summarized previous state (e.g. "PENDING_APPROVAL", "qty: 20")
     * @param afterState   summarized new state (e.g. "APPROVED", "qty: 15")
     * @param details      contextual explanation or reason
     */
    @Transactional
    public void recordBusinessEvent(AuditEventType action,
                                    String resourceType,
                                    Long resourceId,
                                    String status,
                                    String beforeState,
                                    String afterState,
                                    String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        String actorUsername = "SYSTEM";

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal up) {
                userId = up.getUserId();
                actorUsername = up.getUsername();
            } else {
                actorUsername = auth.getName();
            }
        }

        String clientIp = resolveCurrentClientIp();

        TransactionLog auditLog = TransactionLog.builder()
                .timestamp(LocalDateTime.now())
                .action(action.name())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .userId(userId)
                .actorUsername(actorUsername)
                .status(status != null ? status : "SUCCESS")
                .beforeState(beforeState)
                .afterState(afterState)
                .details(truncate(details, 500))
                .ipAddress(clientIp)
                .build();

        transactionLogMapper.insert(auditLog);
        log.debug("Audit event recorded: action={} actor={} resource={}:{} status={}",
                action, actorUsername, resourceType, resourceId, status);
    }

    /**
     * Records successful authentication.
     */
    @Transactional
    public void recordAuthSuccess(Long userId, String username, String clientIp) {
        TransactionLog auditLog = TransactionLog.builder()
                .timestamp(LocalDateTime.now())
                .action(AuditEventType.LOGIN_SUCCESS.name())
                .resourceType("AUTH")
                .resourceId(userId)
                .userId(userId)
                .actorUsername(username)
                .status("SUCCESS")
                .beforeState(null)
                .afterState("AUTHENTICATED")
                .details("User authentication successful")
                .ipAddress(clientIp)
                .build();

        transactionLogMapper.insert(auditLog);
    }

    /**
     * Records failed authentication attempt without storing any password or credential data.
     */
    @Transactional
    public void recordAuthFailure(String attemptedUsername, String clientIp, String reason) {
        String sanitizedUsername = sanitizeIdentifier(attemptedUsername);
        TransactionLog auditLog = TransactionLog.builder()
                .timestamp(LocalDateTime.now())
                .action(AuditEventType.LOGIN_FAILURE.name())
                .resourceType("AUTH")
                .resourceId(null)
                .userId(null)
                .actorUsername(sanitizedUsername)
                .status("FAILURE")
                .beforeState(null)
                .afterState("UNAUTHENTICATED")
                .details(truncate(reason != null ? reason : "Authentication failed", 500))
                .ipAddress(clientIp)
                .build();

        transactionLogMapper.insert(auditLog);
    }

    /**
     * Server-side paginated, filtered, and sorted audit log query for authorized administrators.
     */
    @Transactional(readOnly = true)
    public PagedAuditLogView listAuditLogsPaged(Integer page,
                                                Integer size,
                                                String action,
                                                String resourceType,
                                                Long resourceId,
                                                String actor,
                                                String status,
                                                LocalDateTime startDate,
                                                LocalDateTime endDate) {
        int pageIndex = (page != null) ? page : 0;
        int pageSize = (size != null) ? size : DEFAULT_PAGE_SIZE;

        if (pageIndex < 0) {
            throw new IllegalArgumentException("Page index must not be negative.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least 1.");
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must not exceed " + MAX_PAGE_SIZE + ".");
        }

        int offset = pageIndex * pageSize;

        String sanitizedAction = (action != null && !action.isBlank()) ? action.trim() : null;
        String sanitizedResourceType = (resourceType != null && !resourceType.isBlank()) ? resourceType.trim() : null;
        String sanitizedActor = (actor != null && !actor.isBlank()) ? actor.trim() : null;
        String sanitizedStatus = (status != null && !status.isBlank()) ? status.trim() : null;

        List<AuditLogView> items = transactionLogMapper.findAuditLogsPaged(
                offset, pageSize, sanitizedAction, sanitizedResourceType, resourceId, sanitizedActor, sanitizedStatus, startDate, endDate);
        int totalCount = transactionLogMapper.countAuditLogs(
                sanitizedAction, sanitizedResourceType, resourceId, sanitizedActor, sanitizedStatus, startDate, endDate);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        return new PagedAuditLogView(items, totalCount, pageIndex, pageSize, totalPages);
    }

    @Transactional(readOnly = true)
    public AuditLogView getAuditLogById(Long id) {
        return transactionLogMapper.findAuditLogById(id);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private String resolveCurrentClientIp() {
        RequestAttributes reqAttrs = RequestContextHolder.getRequestAttributes();
        if (reqAttrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            boolean trustProxy = rateLimitProperties != null && rateLimitProperties.isTrustProxy();
            return RateLimitingFilter.resolveClientIp(request, trustProxy);
        }
        return null;
    }

    private String sanitizeIdentifier(String input) {
        if (input == null || input.isBlank()) {
            return "anonymous";
        }
        // Strip control characters and truncate to max length
        String cleaned = input.replaceAll("[\\p{Cntrl}]", "").trim();
        return truncate(cleaned, 100);
    }

    private String truncate(String val, int maxLength) {
        if (val == null) return null;
        return val.length() <= maxLength ? val : val.substring(0, maxLength);
    }
}
