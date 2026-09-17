package com.siteflow.security.ratelimit;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.ErrorDetails;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that enforces server-side rate limiting on API requests.
 * Runs after {@link com.siteflow.security.JwtAuthenticationFilter} so that authenticated
 * user identity is available for rate-limiting authenticated requests.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(
            RateLimitProperties properties,
            RateLimiterService rateLimiterService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Rate limiting is disabled or request is not an API request
        if (!properties.isEnabled() || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Never rate limit CORS preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String clientIp = resolveClientIp(request, properties.isTrustProxy());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authenticatedUser = (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                ? auth.getName()
                : null;

        RateLimitProperties.Policy policy;
        String bucketKey;

        if (uri.equals("/api/auth/login")) {
            policy = properties.getLogin();
            bucketKey = "login:ip:" + clientIp;
        } else if (isSensitiveEndpoint(uri)) {
            policy = properties.getSensitive();
            bucketKey = "sensitive:" + (authenticatedUser != null ? "user:" + authenticatedUser : "ip:" + clientIp);
        } else {
            policy = properties.getApi();
            bucketKey = "api:" + (authenticatedUser != null ? "user:" + authenticatedUser : "ip:" + clientIp);
        }

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(
                bucketKey, policy.getRequests(), policy.getWindowSeconds());

        if (result.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.getRequests()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingRequests()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.getRequests()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorDetails details = new ErrorDetails(
                    LocalDateTime.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests",
                    uri,
                    null);

            objectMapper.writeValue(response.getWriter(), ApiResponse.error("Too many requests. Please try again later.", details));
        }
    }

    private boolean isSensitiveEndpoint(String uri) {
        return uri.startsWith("/api/approvals/")
                || uri.startsWith("/api/stock-adjustments")
                || uri.startsWith("/api/assets/");
    }

    public static String resolveClientIp(HttpServletRequest request, boolean trustProxy) {
        if (request == null) {
            return "unknown";
        }
        if (trustProxy) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }
}
