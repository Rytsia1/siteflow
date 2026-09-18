package com.siteflow.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.web.ApiResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String LOGIN_ENDPOINT = "/api/auth/login";

    private final AuthCookieService authCookieService;
    private final ObjectMapper objectMapper;

    public CsrfProtectionFilter(AuthCookieService authCookieService, ObjectMapper objectMapper) {
        this.authCookieService = authCookieService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 1. Safe HTTP methods do not mutate state
        if (SAFE_METHODS.contains(method)) {
            // If CSRF cookie is absent on a safe request, issue one for the client
            if (authCookieService.extractCsrfCookie(request) == null && !response.isCommitted()) {
                authCookieService.addCsrfCookie(request, response, 3600L);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Initial login endpoint creates the session and is unauthenticated prior to execution
        if (LOGIN_ENDPOINT.equalsIgnoreCase(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Check if request is authenticated via Cookie
        Boolean authViaCookie = (Boolean) request.getAttribute(JwtAuthenticationFilter.AUTH_VIA_COOKIE_ATTR);
        boolean hasAuthCookie = authCookieService.extractAuthToken(request) != null;

        // If the request does not present cookie-based credentials, CSRF protection does not apply.
        // Bearer token requests, public requests, and unauthenticated requests are not vulnerable to browser CSRF.
        if (!hasAuthCookie && !Boolean.TRUE.equals(authViaCookie)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Validate CSRF token for cookie-authenticated mutation requests
        String cookieCsrf = authCookieService.extractCsrfCookie(request);
        String headerCsrf = request.getHeader(AuthCookieService.CSRF_HEADER_NAME);
        if (!StringUtils.hasText(headerCsrf)) {
            headerCsrf = request.getHeader(AuthCookieService.CSRF_HEADER_FALLBACK);
        }

        boolean valid = StringUtils.hasText(cookieCsrf)
                && StringUtils.hasText(headerCsrf)
                && MessageDigest.isEqual(cookieCsrf.getBytes(StandardCharsets.UTF_8), headerCsrf.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            log.warn("CSRF validation failed for {} {} (hasCookie={}, hasHeader={})",
                    method, uri, StringUtils.hasText(cookieCsrf), StringUtils.hasText(headerCsrf));
            SecurityContextHolder.clearContext();

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            var errorDetails = java.util.Map.of(
                    "status", HttpServletResponse.SC_FORBIDDEN,
                    "error", "Forbidden",
                    "message", "CSRF token validation failed. Mutating requests using cookie authentication require a matching X-XSRF-TOKEN header."
            );
            ApiResponse<?> apiResponse = ApiResponse.error("Invalid or missing CSRF token.", errorDetails);
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
