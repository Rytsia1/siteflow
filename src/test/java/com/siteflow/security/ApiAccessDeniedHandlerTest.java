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
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ApiAccessDeniedHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(objectMapper);

    @Test
    @DisplayName("Filter-chain access denial returns the standard ApiResponse envelope with 403 Forbidden")
    void handle_writesStandardEnvelope() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/analytics/summary");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);

        JsonNode json = objectMapper.readTree(body.toString());
        assertThat(json.get("status").asText()).isEqualTo("error");
        assertThat(json.get("message").asText()).isEqualTo("Access denied.");
        assertThat(json.get("data").get("status").asInt()).isEqualTo(403);
        assertThat(json.get("data").get("error").asText()).isEqualTo("Forbidden");
        assertThat(json.get("data").get("path").asText()).isEqualTo("/api/analytics/summary");
        assertThat(json.get("data").get("timestamp")).isNotNull();
    }
}
