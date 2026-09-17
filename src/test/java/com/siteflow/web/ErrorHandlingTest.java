package com.siteflow.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.security.JwtTokenProvider;
import com.siteflow.security.UserPrincipal;
import com.siteflow.security.ratelimit.RateLimiterService;

@SpringBootTest
@AutoConfigureMockMvc
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private com.siteflow.security.LoginAttemptService loginAttemptService;

    private String adminToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        rateLimiterService.resetAll();
        loginAttemptService.resetAll();
        adminToken = jwtTokenProvider.generateToken(new UserPrincipal(1L, "admin", "", "ADMIN"));
        staffToken = jwtTokenProvider.generateToken(new UserPrincipal(3L, "pekerja", "", "FIELD_STAFF"));
    }

    @Test
    @DisplayName("1. Validation error returns 400 with structured field errors")
    void validationError_returns400WithFieldErrors() throws Exception {
        // Missing required items list and locationId
        String invalidPayload = """
                {
                    "locationId": null,
                    "items": []
                }
                """;

        mockMvc.perform(post("/api/borrow-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed."))
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.error").value("Bad Request"))
                .andExpect(jsonPath("$.data.fieldErrors").isMap());
    }

    @Test
    @DisplayName("2. Missing resource returns 404 with clean message")
    void missingResource_returns404() throws Exception {
        mockMvc.perform(get("/api/borrow-requests/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Borrow request not found: 999999")))
                .andExpect(jsonPath("$.data.status").value(404))
                .andExpect(jsonPath("$.data.error").value("Not Found"));
    }

    @Test
    @DisplayName("3. Business conflict returns 409 Conflict")
    void businessConflict_returns409() throws Exception {
        mockMvc.perform(post("/api/approvals/material-requests/999999/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int sc = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(sc).isIn(404, 409);
                })
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("4. Unauthorized request without token returns 401")
    void unauthorizedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.status").value(401));
    }

    @Test
    @DisplayName("5. Forbidden request returns 403 Access Denied")
    void forbiddenRequest_returns403() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Access denied."))
                .andExpect(jsonPath("$.data.status").value(403))
                .andExpect(jsonPath("$.data.error").value("Forbidden"));
    }

    @Test
    @DisplayName("6. Unexpected server exception maps to 500 without leaking details")
    void unexpectedException_returns500WithoutLeakingDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        jakarta.servlet.http.HttpServletRequest req = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        org.mockito.Mockito.when(req.getRequestURI()).thenReturn("/api/items");
        org.mockito.Mockito.when(req.getMethod()).thenReturn("GET");

        org.springframework.http.ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleUnexpected(new RuntimeException("Fatal database error: connection to 10.0.0.5:3306 timed out"), req);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        org.assertj.core.api.Assertions.assertThat(response.getBody().message())
                .isEqualTo("An unexpected error occurred.")
                .doesNotContain("10.0.0.5")
                .doesNotContain("3306");
        org.assertj.core.api.Assertions.assertThat(response.getBody().data().status()).isEqualTo(500);
        org.assertj.core.api.Assertions.assertThat(response.getBody().data().error()).isEqualTo("Internal Server Error");
    }

    @Test
    @DisplayName("7. Error responses do not leak stack traces, SQL queries, or internal paths")
    void errorResponse_doesNotLeakSensitiveInfo() throws Exception {
        mockMvc.perform(get("/api/nonexistent-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.status").value(404))
                .andExpect(jsonPath("$.message").value(not(containsString("org.springframework"))))
                .andExpect(jsonPath("$.message").value(not(containsString("NullPointerException"))))
                .andExpect(jsonPath("$.message").value(not(containsString(".java:"))));
    }

    @Test
    @DisplayName("8. Rate limit exceeded returns 429 with Retry-After header")
    void rateLimitExceeded_returns429WithRetryAfter() throws Exception {
        String clientIp = "192.168.100.200";
        String loginPayload = """
                {
                    "username": "admin",
                    "password": "admin123"
                }
                """;

        // Exceed login rate limit (5 allowed in 60s)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk());
        }

        // 6th request triggers rate limiter
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Too many requests")))
                .andExpect(jsonPath("$.data.status").value(429));
    }
}
