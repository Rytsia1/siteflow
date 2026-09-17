package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siteflow.web.dto.LoginRequestDto;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private com.siteflow.security.ratelimit.RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        loginAttemptService.resetAll();
        rateLimiterService.resetAll();
    }

    @Nested
    @DisplayName("1 & 2. JWT Validation Hardening")
    class JwtValidationTests {

        @Test
        @DisplayName("Invalid / malformed / tampered JWT is rejected with 401 Unauthorized")
        void invalidJwt_isRejectedWith401() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.malformed.token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Authentication required."))
                    .andExpect(jsonPath("$.data.status").value(401));

            // Tampered signature
            UserPrincipal principal = new UserPrincipal(1L, "admin", "", "ADMIN");
            String validToken = jwtTokenProvider.generateToken(principal);
            String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.data.status").value(401));
        }

        @Test
        @DisplayName("Expired JWT is rejected with 401 Unauthorized")
        void expiredJwt_isRejectedWith401() throws Exception {
            UserPrincipal principal = new UserPrincipal(1L, "admin", "", "ADMIN");
            // Generate token expired 10 seconds ago
            String expiredToken = jwtTokenProvider.generateToken(principal, -10000L);

            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Authentication required."))
                    .andExpect(jsonPath("$.data.status").value(401));
        }
    }

    @Nested
    @DisplayName("3 & 4. Protected Endpoints & Authorization")
    class AuthorizationTests {

        @Test
        @DisplayName("Protected endpoint cannot be accessed without authentication (401)")
        void unauthenticatedAccess_returns401() throws Exception {
            mockMvc.perform(get("/api/items"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Authentication required."));
        }

        @Test
        @DisplayName("Unauthorized role receives 403 Forbidden")
        void unauthorizedRole_returns403() throws Exception {
            UserPrincipal fieldWorker = new UserPrincipal(3L, "pekerja", "", "FIELD_STAFF");
            String workerToken = jwtTokenProvider.generateToken(fieldWorker);

            // FIELD_STAFF attempting access to ADMIN-only analytics
            mockMvc.perform(get("/api/analytics/summary")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + workerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Access denied."))
                    .andExpect(jsonPath("$.data.status").value(403));
        }
    }

    @Nested
    @DisplayName("5. Password Security & Response Leakage")
    class PasswordSecurityTests {

        @Test
        @DisplayName("LoginRequestDto toString masks plain-text password")
        void loginRequestDto_masksPasswordInToString() {
            LoginRequestDto dto = new LoginRequestDto("admin", "SuperSecretPassword123!");
            String str = dto.toString();
            assertThat(str).contains("username=admin");
            assertThat(str).contains("password=***");
            assertThat(str).doesNotContain("SuperSecretPassword123!");
        }

        @Test
        @DisplayName("Password is never exposed in login response or /me endpoint")
        void passwordNeverExposedInResponses() throws Exception {
            String loginPayload = """
                    {
                        "username": "admin",
                        "password": "admin123"
                    }
                    """;

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String loginBody = loginResult.getResponse().getContentAsString();
            assertThat(loginBody).doesNotContain("password");
            assertThat(loginBody).doesNotContain("admin123");
            assertThat(loginBody).doesNotContain("$2");

            JsonNode rootNode = objectMapper.readTree(loginBody);
            String token = rootNode.path("data").path("accessToken").asText();

            MvcResult meResult = mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();

            String meBody = meResult.getResponse().getContentAsString();
            assertThat(meBody).doesNotContain("password");
            assertThat(meBody).doesNotContain("admin123");
            assertThat(meBody).doesNotContain("$2");
        }
    }

    @Nested
    @DisplayName("6 & 7. Input Validation & Safe Error Responses")
    class InputValidationAndErrorHandlingTests {

        @Test
        @DisplayName("Authentication errors return standard envelope without internal class names or stack traces")
        void authErrors_doNotExposeInternalDetails() throws Exception {
            String badCredsPayload = """
                    {
                        "username": "admin",
                        "password": "wrongpassword"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badCredsPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Invalid username or password."))
                    .andExpect(jsonPath("$.data.status").value(401))
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("org.springframework");
            assertThat(body).doesNotContain("BadCredentialsException");
            assertThat(body).doesNotContain("stackTrace");
            assertThat(body).doesNotContain("Exception");
        }

        @Test
        @DisplayName("Invalid input (blank/oversized username or password) is rejected with 400 Bad Request")
        void invalidInput_rejectedWith400() throws Exception {
            // Blank username
            String blankUsername = """
                    {
                        "username": "",
                        "password": "somepassword"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(blankUsername))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Validation failed."))
                    .andExpect(jsonPath("$.data.fieldErrors.username", not(nullValue())));

            // Blank password
            String blankPassword = """
                    {
                        "username": "admin",
                        "password": ""
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(blankPassword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("Validation failed."))
                    .andExpect(jsonPath("$.data.fieldErrors.password", not(nullValue())));

            // Oversized password (> 128 chars)
            String oversizedPassword = """
                    {
                        "username": "admin",
                        "password": "%s"
                    }
                    """.formatted("a".repeat(129));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(oversizedPassword))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.data.fieldErrors.password", containsString("exceed 128 characters")));
        }
    }

    @Nested
    @DisplayName("8. CORS Origin Enforcement")
    class CorsTests {

        @Test
        @DisplayName("Configured development origin receives matching Access-Control-Allow-Origin")
        void configuredOrigin_receivesCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }

        @Test
        @DisplayName("Disallowed origin does NOT receive Access-Control-Allow-Origin header")
        void disallowedOrigin_isNotGrantedCors() throws Exception {
            mockMvc.perform(options("/api/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://malicious-site.com")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Nested
    @DisplayName("9. Security Response Headers")
    class SecurityHeadersTests {

        @Test
        @DisplayName("Responses contain standard HTTP security hardening headers")
        void securityHeaders_arePresent() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                    .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
        }
    }

    @Nested
    @DisplayName("10. Authentication Abuse & Brute-Force Lockout")
    class BruteForceProtectionTests {

        @Test
        @DisplayName("5 consecutive failed logins trigger HTTP 429 Too Many Requests")
        void excessiveFailedLogins_triggers429() throws Exception {
            String testIp = "192.168.10.50";
            String wrongCreds = """
                    {
                        "username": "admin",
                        "password": "wrongpassword"
                    }
                    """;

            // First 4 failed attempts receive 401 Unauthorized
            for (int i = 1; i <= 4; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(request -> {
                                    request.setRemoteAddr(testIp);
                                    return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongCreds))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.status").value("error"))
                        .andExpect(jsonPath("$.data.status").value(401));
            }

            // 5th attempt fails and locks out the IP
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCreds))
                    .andExpect(status().isUnauthorized());

            // 6th attempt is blocked before authentication with 429 Too Many Requests
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongCreds))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message", containsString("Too many")))
                    .andExpect(jsonPath("$.data.status").value(429))
                    .andExpect(jsonPath("$.data.error").value("Too Many Requests"));

            // A different client IP is NOT locked out
            String differentIp = "192.168.10.51";
            String validAdmin = """
                    {
                        "username": "admin",
                        "password": "admin123"
                    }
                    """;

            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(differentIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Successful login clears failed attempt counter")
        void successfulLogin_clearsCounter() throws Exception {
            String testIp = "192.168.10.60";
            String wrongCreds = """
                    {
                        "username": "admin",
                        "password": "wrongpassword"
                    }
                    """;
            String validCreds = """
                    {
                        "username": "admin",
                        "password": "admin123"
                    }
                    """;

            // 2 failed attempts
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(request -> {
                                    request.setRemoteAddr(testIp);
                                    return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongCreds))
                        .andExpect(status().isUnauthorized());
            }

            // Successful login resets counter
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validCreds))
                    .andExpect(status().isOk());

            // Reset request-frequency rate limiter to test login attempt counter reset in isolation
            rateLimiterService.resetAll();

            // Should be able to make 4 more failed attempts without being locked out
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .with(request -> {
                                    request.setRemoteAddr(testIp);
                                    return request;
                                })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongCreds))
                        .andExpect(status().isUnauthorized());
            }
        }
    }
}
