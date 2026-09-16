package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Verifies the fix for the gap the frontend's http.js interceptor explicitly warned
 * about: a missing/invalid Authorization header used to return an empty 401 body,
 * bypassing GlobalExceptionHandler entirely.
 */
@ExtendWith(MockitoExtension.class)
class ApiAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);

    @Test
    @DisplayName("Missing/invalid credentials return the standard ApiResponse envelope, not an empty body")
    void commence_writesStandardEnvelope() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/items");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setHeader("WWW-Authenticate", "Basic realm=\"siteflow\"");

        JsonNode json = objectMapper.readTree(body.toString());
        assertThat(json.get("status").asText()).isEqualTo("error");
        assertThat(json.get("message").asText()).isEqualTo("Authentication required.");
        assertThat(json.get("data").get("status").asInt()).isEqualTo(401);
        assertThat(json.get("data").get("path").asText()).isEqualTo("/api/items");
    }
}
