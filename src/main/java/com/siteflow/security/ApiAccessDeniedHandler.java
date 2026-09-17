package com.siteflow.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.ErrorDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles {@link AccessDeniedException} thrown at the Spring Security filter-chain level.
 * Controller-level {@code @PreAuthorize} access denials are handled by
 * {@link com.siteflow.web.GlobalExceptionHandler}, but this ensures filter-chain denials
 * return the identical standard {@link ApiResponse}/{@link ErrorDetails} envelope.
 */
@Slf4j
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String caller = (auth != null) ? auth.getName() : "anonymous";
        log.warn("Access denied for caller '{}' on {} from IP: {}", caller, request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorDetails details = new ErrorDetails(
                LocalDateTime.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "Forbidden",
                request.getRequestURI(),
                null);

        objectMapper.writeValue(response.getWriter(), ApiResponse.error("Access denied.", details));
    }
}
