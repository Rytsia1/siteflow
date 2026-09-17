package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private com.siteflow.security.ratelimit.RateLimiterService rateLimiterService;

    @org.junit.jupiter.api.BeforeEach
    void resetRateLimiter() {
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
    }

    @Test
    @DisplayName("1. Successful login returns JWT access token with Bearer type and expiration")
    void login_validCredentials_returnsJwtEnvelope() throws Exception {
        String loginJson = """
                {
                    "username": "admin",
                    "password": "admin123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn", greaterThan(0)))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("2. Invalid credentials are rejected with 401 Unauthorized")
    void login_invalidCredentials_returns401() throws Exception {
        // Wrong password for existing user
        String wrongPasswordJson = """
                {
                    "username": "admin",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andExpect(jsonPath("$.data.status").value(401));

        // Non-existent username
        String nonExistentUserJson = """
                {
                    "username": "nonexistent_user",
                    "password": "somepassword"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonExistentUserJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("3. Request without JWT to a protected endpoint returns 401 with Bearer WWW-Authenticate header")
    void request_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."))
                .andExpect(jsonPath("$.data.status").value(401));
    }

    @Test
    @DisplayName("4. Valid JWT allows access to protected endpoints and identifies caller")
    void request_withValidJwt_allowsAccess() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "admin", "", "ADMIN");
        String token = jwtTokenProvider.generateToken(principal);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    @DisplayName("5. Malformed JWT is rejected with 401 Unauthorized")
    void request_withMalformedJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.malformed.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    @DisplayName("6. Expired JWT is rejected with 401 Unauthorized")
    void request_withExpiredJwt_returns401() throws Exception {
        UserPrincipal principal = new UserPrincipal(1L, "admin", "", "ADMIN");
        // Expired 10 seconds ago
        String expiredToken = jwtTokenProvider.generateToken(principal, -10000);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    @DisplayName("7. Password and password hash are never returned in authentication responses or tokens")
    void auth_neverExposesPassword() throws Exception {
        String loginJson = """
                {
                    "username": "admin",
                    "password": "admin123"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.path("data").path("accessToken").asText();

        // Inspect token claims payload
        Claims claims = jwtTokenProvider.getClaims(token);
        assertThat(claims.get("password")).isNull();
        assertThat(claims.get("passwordHash")).isNull();

        // Inspect /api/auth/me response
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("8. JWT secret is loaded from configuration and is never hardcoded")
    void jwtSecret_isNotHardcoded() throws IOException {
        // Verify provider throws if secret is missing/blank
        assertThatThrownBy(() -> new JwtTokenProvider(null, 3600000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret is not configured");

        assertThatThrownBy(() -> new JwtTokenProvider("", 3600000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret is not configured");

        // Verify provider throws if secret is under 256 bits (32 bytes)
        assertThatThrownBy(() -> new JwtTokenProvider("short-secret-under-32-chars", 3600000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 256 bits");

        // Inspect production application.yml: verify it strictly references ${JWT_SECRET} without hardcoded fallback
        Path prodYml = Path.of("src/main/resources/application.yml");
        assertThat(prodYml).exists();
        String ymlContent = Files.readString(prodYml);
        assertThat(ymlContent).contains("secret: ${JWT_SECRET}");
        assertThat(ymlContent).doesNotContain("secret: ${JWT_SECRET:");

        // Scan all production java source files to verify no literal secret strings
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String code = Files.readString(p);
                            assertThat(code)
                                    .as("Hardcoded secret in " + p)
                                    .doesNotContain("secret-key")
                                    .doesNotContain("mysecret")
                                    .doesNotContain("jwt-secret");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
