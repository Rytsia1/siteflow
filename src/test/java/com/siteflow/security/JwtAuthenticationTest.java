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

    @Autowired
    private com.siteflow.mapper.UserMapper userMapper;

    @Autowired
    private com.siteflow.service.UserService userService;

    private Long createTestUser(String username, String roleName, boolean isActive, int tokenVersion) {
        Long roleId = userMapper.findRoleIdByName(roleName);
        com.siteflow.domain.User user = com.siteflow.domain.User.builder()
                .roleId(roleId)
                .username(username)
                .passwordHash("$2b$10$TfwVMtix/ZbXNl/rYEnVYuhaA9LCKcysxnCRX2mOo0n8..TodUb/6")
                .fullName("Test " + username)
                .jobPosition("Tester")
                .isActive(isActive)
                .tokenVersion(tokenVersion)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        userMapper.insertUser(user);
        return user.getId();
    }

    @Test
    @DisplayName("9. Deactivated user with previously issued JWT is rejected with 401 Unauthorized")
    void deactivatedUser_previouslyIssuedJwt_isRejectedWith401() throws Exception {
        String username = "temp_user_deact_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "FIELD_STAFF", true, 1);

        UserPrincipal principal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, 1);
        String token = jwtTokenProvider.generateToken(principal);

        // Verify token works while active
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        // Deactivate user account
        userService.deactivateAndAnonymizeUser(userId);

        // Previously issued token must now be rejected with 401
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    @DisplayName("10. Token version mismatch is rejected with 401 Unauthorized")
    void tokenVersionMismatch_isRejectedWith401() throws Exception {
        String username = "temp_user_ver_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "FIELD_STAFF", true, 2);

        // Token has version 1, but database has version 2
        UserPrincipal principal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, 1);
        String staleToken = jwtTokenProvider.generateToken(principal);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    @DisplayName("11. Newly issued token after valid security-state change works successfully")
    void newlyIssuedToken_afterSecurityStateChange_works() throws Exception {
        String username = "temp_user_refresh_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "FIELD_STAFF", true, 1);

        UserPrincipal oldPrincipal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, 1);
        String oldToken = jwtTokenProvider.generateToken(oldPrincipal);

        // Revoke active sessions
        userService.revokeUserTokens(userId);

        // Old token rejected
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());

        // Re-fetch current state from database
        com.siteflow.domain.UserWithRole userInDb = userMapper.findUserWithRoleById(userId);
        assertThat(userInDb.getTokenVersion()).isEqualTo(2);

        // Newly issued token with matching token_version succeeds
        UserPrincipal newPrincipal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, userInDb.getTokenVersion());
        String newToken = jwtTokenProvider.generateToken(newPrincipal);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.userId").value(userId));
    }

    @Test
    @DisplayName("12. Role change invalidates old security state and tokens")
    void roleChange_invalidatesOldSecurityState() throws Exception {
        String username = "temp_user_role_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "ADMIN", true, 1);

        UserPrincipal adminPrincipal = new UserPrincipal(userId, username, "", "ADMIN", true, 1);
        String adminToken = jwtTokenProvider.generateToken(adminPrincipal);

        // Demote to FIELD_STAFF (increments token_version)
        userService.updateUserRole(userId, "FIELD_STAFF");

        // Old admin token is rejected
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());

        com.siteflow.domain.UserWithRole updatedUser = userMapper.findUserWithRoleById(userId);
        assertThat(updatedUser.getRoleName()).isEqualTo("FIELD_STAFF");
        assertThat(updatedUser.getTokenVersion()).isEqualTo(2);

        // Token claiming ADMIN with new version rejected due to role contradiction
        UserPrincipal forgedAdminPrincipal = new UserPrincipal(userId, username, "", "ADMIN", true, updatedUser.getTokenVersion());
        String forgedToken = jwtTokenProvider.generateToken(forgedAdminPrincipal);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());

        // Legitimate token with current role succeeds
        UserPrincipal legitimateStaffPrincipal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, updatedUser.getTokenVersion());
        String legitimateToken = jwtTokenProvider.generateToken(legitimateStaffPrincipal);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + legitimateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("FIELD_STAFF"));

        // Field staff token cannot access ADMIN-only endpoint (403 Forbidden)
        mockMvc.perform(post("/api/users/1/deactivate")
                        .header("Authorization", "Bearer " + legitimateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    @DisplayName("13. Revocation and error responses do not leak sensitive account or internal state")
    void errorResponses_doNotLeakSensitiveInformation() throws Exception {
        String username = "temp_user_leak_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "FIELD_STAFF", true, 1);
        userService.deactivateAndAnonymizeUser(userId);

        UserPrincipal principal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, 1);
        String token = jwtTokenProvider.generateToken(principal);

        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("token_version");
        assertThat(body).doesNotContain("tokenVersion");
        assertThat(body).doesNotContain("deactivated");
        assertThat(body).doesNotContain("is_active");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("users");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("SELECT");
    }

    @Test
    @DisplayName("14. Logout endpoint revokes active session immediately")
    void logout_revokesActiveTokenImmediately() throws Exception {
        String username = "temp_user_logout_" + System.currentTimeMillis();
        Long userId = createTestUser(username, "FIELD_STAFF", true, 1);

        UserPrincipal principal = new UserPrincipal(userId, username, "", "FIELD_STAFF", true, 1);
        String token = jwtTokenProvider.generateToken(principal);

        // Verify authentication works
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Perform logout
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Reusing the token fails immediately
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }
}
