package com.siteflow.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.ErrorDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Replaces Spring Security's default HTTP Basic entry point, which sends a 401 with an
 * empty body. A missing/invalid Authorization header is rejected by the security filter
 * chain before the request ever reaches a controller, so GlobalExceptionHandler never
 * sees it — this is the one error path that has to be handled at the security layer
 * instead, but it returns the exact same ApiResponse/ErrorDetails envelope as every
 * other error response.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Preserve the standard Basic-auth challenge header even though this is a JSON API.
        response.setHeader("WWW-Authenticate", "Basic realm=\"siteflow\"");

        ErrorDetails details = new ErrorDetails(LocalDateTime.now(), HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized", request.getRequestURI(), null);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error("Authentication required.", details));
    }
}
