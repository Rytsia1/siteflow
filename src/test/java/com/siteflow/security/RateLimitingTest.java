package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.siteflow.security.ratelimit.RateLimitProperties;
import com.siteflow.security.ratelimit.RateLimiterService;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        rateLimiterService.resetAll();
        loginAttemptService.resetAll();
    }

    @Nested
    @DisplayName("1, 2 & 5. Rate Limiting Quota and Retry-After Header")
    class RateLimitingQuotaTests {

        @Test
        @DisplayName("Requests below the limit succeed, exceeding limit returns 429 with Retry-After")
        void requestsExceedingLimit_return429WithRetryAfter() throws Exception {
            String clientIp = "10.0.0.42";
            String validPayload = """
                    {
                        "username": "admin",
                        "password": "admin123"
                    }
                    """;

            int loginLimit = rateLimitProperties.getLogin().getRequests(); // 5

            // First 5 requests within limit succeed
            for (int i = 0; i < loginLimit; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPayload))
                        .andExpect(status().isOk())
                        .andExpect(header().exists("X-RateLimit-Limit"))
                        .andExpect(header().exists("X-RateLimit-Remaining"));
            }

            // 6th request exceeds the quota and returns 429 Too Many Requests
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPayload))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."))
                    .andExpect(jsonPath("$.data.status").value(429))
                    .andExpect(jsonPath("$.data.error").value("Too Many Requests"));
        }
    }

    @Nested
    @DisplayName("3. Login Stricter Protection vs General API")
    class ComparativePolicyTests {

        @Test
        @DisplayName("Login limit is significantly stricter than general API limit")
        void loginHasStricterProtectionThanGeneralApi() throws Exception {
            assertThat(rateLimitProperties.getLogin().getRequests())
                    .isLessThan(rateLimitProperties.getApi().getRequests());

            UserPrincipal admin = new UserPrincipal(1L, "admin", "", "ADMIN");
            String token = jwtTokenProvider.generateToken(admin);

            // 15 requests to general API succeed well beyond login limit (5)
            for (int i = 0; i < 15; i++) {
                mockMvc.perform(get("/api/items")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    @Nested
    @DisplayName("4. Configuration Flexibility")
    class ConfigurationTests {

        @Test
        @DisplayName("Rate-limit configuration is loaded from properties and can be dynamically configured")
        void rateLimitConfiguration_isLoadedFromProperties() {
            assertThat(rateLimitProperties.isEnabled()).isTrue();
            assertThat(rateLimitProperties.getLogin().getRequests()).isEqualTo(5);
            assertThat(rateLimitProperties.getLogin().getWindowSeconds()).isEqualTo(60);
            assertThat(rateLimitProperties.getApi().getRequests()).isEqualTo(100);
            assertThat(rateLimitProperties.getApi().getWindowSeconds()).isEqualTo(60);
            assertThat(rateLimitProperties.getSensitive().getRequests()).isEqualTo(30);
            assertThat(rateLimitProperties.getSensitive().getWindowSeconds()).isEqualTo(60);
        }
    }

    @Nested
    @DisplayName("5. Client Identification and Proxy Trust")
    class ClientIdentificationTests {

        @Test
        @DisplayName("X-Forwarded-For is ignored when trustProxy is false")
        void untrustedProxyHeader_isIgnored() throws Exception {
            assertThat(rateLimitProperties.isTrustProxy()).isFalse();

            String clientIp = "10.0.0.99";
            String fakeProxyIp = "203.0.113.195";
            String validPayload = """
                    {
                        "username": "admin",
                        "password": "admin123"
                    }
                    """;

            // Exhaust limit using clientIp while attempting to spoof X-Forwarded-For
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(request -> {
                                    request.setRemoteAddr(clientIp);
                                    return request;
                                })
                                .header("X-Forwarded-For", fakeProxyIp)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validPayload))
                        .andExpect(status().isOk());
            }

            // Attempting another request with a different spoofed X-Forwarded-For still hits the 429
            // because rate limiter keys on actual remote socket address clientIp
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            })
                            .header("X-Forwarded-For", "198.51.100.22")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validPayload))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("6. Excessive Pagination Size Limits")
    class PaginationLimitsTests {

        @Test
        @DisplayName("Excessive pagination size is rejected with 400 Bad Request")
        void excessivePaginationSize_isRejected() throws Exception {
            UserPrincipal worker = new UserPrincipal(3L, "pekerja", "", "FIELD_STAFF");
            String token = jwtTokenProvider.generateToken(worker);

            // Size 1,000,000 is rejected
            mockMvc.perform(get("/api/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("page", "0")
                            .param("size", "1000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(containsString("Page size must not exceed 100")));

            // Negative page index is rejected
            mockMvc.perform(get("/api/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("page", "-1")
                            .param("size", "10"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(containsString("Page index must not be negative")));

            // Size 0 is rejected
            mockMvc.perform(get("/api/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("page", "0")
                            .param("size", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(containsString("Page size must be at least 1")));

            // Bounded pagination within limits succeeds
            mockMvc.perform(get("/api/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data", hasSize(2)));
        }

        @Test
        @DisplayName("Excessive analytics date range is rejected with 400 Bad Request")
        void excessiveDateRange_isRejected() throws Exception {
            UserPrincipal admin = new UserPrincipal(1L, "admin", "", "ADMIN");
            String token = jwtTokenProvider.generateToken(admin);

            // Inverted dates rejected
            mockMvc.perform(get("/api/analytics/trends")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("startDate", "2026-12-31")
                            .param("endDate", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(containsString("Start date must be before or equal to end date")));

            // Exceeding 5 years rejected
            mockMvc.perform(get("/api/analytics/trends")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .param("startDate", "2015-01-01")
                            .param("endDate", "2026-01-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value(containsString("Date range must not exceed 5 years")));
        }
    }

    @Nested
    @DisplayName("7. Normal Frontend API Usage")
    class FrontendCompatibilityTests {

        @Test
        @DisplayName("Unpaginated API calls from frontend remain fully functional")
        void unpaginatedCalls_succeed() throws Exception {
            UserPrincipal worker = new UserPrincipal(3L, "pekerja", "", "FIELD_STAFF");
            String token = jwtTokenProvider.generateToken(worker);

            // Frontend calls /api/items without parameters
            mockMvc.perform(get("/api/items")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }
}
