package com.siteflow.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that establishes and propagates a Correlation/Request ID across the request lifecycle.
 * - Extracts client-provided X-Request-ID if valid, or generates a UUID.
 * - Injects the ID into SLF4J MDC for contextual logging.
 * - Attaches the ID to the HTTP response header for client-side correlation.
 * - Cleans up MDC after request completion.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ATTR_KEY = "correlationId";

    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = extractOrGenerateRequestId(request);

        MDC.put(MDC_KEY, requestId);
        request.setAttribute(REQUEST_ATTR_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String extractOrGenerateRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (header != null) {
            String trimmed = header.trim();
            if (VALID_ID_PATTERN.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }
}
