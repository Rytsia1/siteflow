package com.siteflow.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
import org.springframework.test.web.servlet.MvcResult;

import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;
import com.siteflow.security.ratelimit.RateLimiterService;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
public class CookieAuthenticationAndCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private RateLimiterService rateLimiterService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        if (rateLimiterService != null) {
            rateLimiterService.resetAll();
        }
        resetUsers();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        resetUsers();
    }

    private void resetUsers() {
        jdbcTemplate.update("UPDATE users SET token_version = 1, is_active = TRUE WHERE id IN (1, 2, 3)");
    }

    private String createValidToken(String username) {
        UserWithRole user = userMapper.findByUsername(username);
        int tokenVer = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), "", user.getRoleName(), true, tokenVer);
        return jwtTokenProvider.generateToken(principal);
    }

    @Test
    @DisplayName("1. Login issues HttpOnly, SameSite=Lax auth cookie and readable CSRF cookie")
    void login_issuesAuthAndCsrfCookies() throws Exception {
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
                .andExpect(cookie().exists("siteflow_token"))
                .andExpect(cookie().httpOnly("siteflow_token", true))
                .andExpect(cookie().path("siteflow_token", "/"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().path("XSRF-TOKEN", "/"))
                .andReturn();

        // Verify Set-Cookie header contains SameSite=Lax
        var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(c -> c.contains("siteflow_token") && c.contains("SameSite=Lax") && c.contains("HttpOnly"));
        assertThat(setCookies).anyMatch(c -> c.contains("XSRF-TOKEN") && c.contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("2. Authenticated GET API call with siteflow_token cookie succeeds without Authorization header")
    void authenticatedGet_withCookie_succeeds() throws Exception {
        String token = createValidToken("admin");

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("siteflow_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                // Ensure the token itself is NOT exposed in the response body
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    @DisplayName("3. Authenticated mutating POST request with cookie AND matching CSRF header succeeds")
    void mutatingPost_withCookieAndMatchingCsrf_succeeds() throws Exception {
        String token = createValidToken("admin");
        String csrfToken = "valid-csrf-token-12345";

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("siteflow_token", token), new Cookie("XSRF-TOKEN", csrfToken))
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("4. Cookie-authenticated mutating request WITHOUT CSRF header is rejected with 403 Forbidden")
    void mutatingPost_withCookieMissingCsrf_rejectedWith403() throws Exception {
        String token = createValidToken("admin");
        String csrfToken = "valid-csrf-token-12345";

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("siteflow_token", token), new Cookie("XSRF-TOKEN", csrfToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("CSRF")));
    }

    @Test
    @DisplayName("5. Cookie-authenticated mutating request with MISMATCHED CSRF token is rejected with 403 Forbidden")
    void mutatingPost_withCookieMismatchedCsrf_rejectedWith403() throws Exception {
        String token = createValidToken("admin");

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("siteflow_token", token), new Cookie("XSRF-TOKEN", "real-token-aaa"))
                        .header("X-XSRF-TOKEN", "attacker-token-bbb"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message", containsString("CSRF")));
    }

    @Test
    @DisplayName("6. Logout clears both siteflow_token and XSRF-TOKEN cookies and revokes session")
    void logout_clearsCookiesAndRevokesSession() throws Exception {
        // 1. Perform login
        String loginJson = """
                {
                    "username": "gudang",
                    "password": "gudang123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        Cookie authCookie = loginResult.getResponse().getCookie("siteflow_token");
        Cookie csrfCookie = loginResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(authCookie).isNotNull();
        assertThat(csrfCookie).isNotNull();

        // 2. Perform logout
        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(authCookie, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();

        // Verify cookies cleared with maxAge 0
        Cookie clearedAuth = logoutResult.getResponse().getCookie("siteflow_token");
        Cookie clearedCsrf = logoutResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(clearedAuth).isNotNull();
        assertThat(clearedAuth.getMaxAge()).isZero();
        assertThat(clearedCsrf).isNotNull();
        assertThat(clearedCsrf.getMaxAge()).isZero();

        // 3. Verify old auth cookie is now rejected (token version incremented)
        mockMvc.perform(get("/api/auth/me")
                        .cookie(authCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Expired session cookie returns 401 Unauthorized")
    void expiredSessionCookie_returns401() throws Exception {
        UserWithRole user = userMapper.findByUsername("admin");
        int tokenVer = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), "", user.getRoleName(), true, tokenVer);
        String expiredToken = jwtTokenProvider.generateToken(principal, -3600000L); // expired 1 hour ago

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("siteflow_token", expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("8. Unauthorized request without cookies or headers returns 401")
    void unauthorizedRequest_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer realm=\"siteflow\"")));
    }

    @Test
    @DisplayName("9. Role authorization is enforced when caller authenticates via cookie")
    void roleAuthorization_withCookie_enforcesRbac() throws Exception {
        String staffToken = createValidToken("pekerja");

        // FIELD_STAFF attempting to access ADMIN-only /api/audit-logs endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/audit-logs")
                        .cookie(new Cookie("siteflow_token", staffToken)))
                .andExpect(status().isForbidden());

        String adminToken = createValidToken("admin");

        // ADMIN accessing /api/audit-logs endpoint -> 200 OK
        mockMvc.perform(get("/api/audit-logs")
                        .cookie(new Cookie("siteflow_token", adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. Bearer token header bypasses CSRF checks for non-browser programmatic API clients")
    void bearerToken_bypassesCsrfValidation() throws Exception {
        String token = createValidToken("admin");

        // Mutating request with Bearer token and NO CSRF header succeeds
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    @DisplayName("11. GET /api/auth/csrf endpoint issues a readable CSRF cookie")
    void csrfEndpoint_issuesCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.csrfToken", notNullValue()));
    }

    @Test
    @DisplayName("12. Disallowed cross-origin request is rejected by CORS origin check")
    void crossOrigin_disallowedOrigin_isNotGrantedCors() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://malicious-attacker.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
