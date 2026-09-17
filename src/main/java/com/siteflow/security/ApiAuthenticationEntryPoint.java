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
 * Authentication entry point for unauthenticated requests rejecting missing or invalid Bearer tokens.
 * A missing/invalid Authorization header is rejected by the security filter
 * chain before the request ever reaches a controller, returning the standard
 * ApiResponse/ErrorDetails envelope.
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
        // Send the standard Bearer challenge header for stateless JWT authentication.
        response.setHeader("WWW-Authenticate", "Bearer realm=\"siteflow\"");

        ErrorDetails details = new ErrorDetails(LocalDateTime.now(), HttpServletResponse.SC_UNAUTHORIZED,
                "Unauthorized", request.getRequestURI(), null);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error("Authentication required.", details));
    }
}
