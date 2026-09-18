package com.siteflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.security.JwtTokenProvider;
import com.siteflow.security.LoginAttemptService;
import com.siteflow.security.UserPrincipal;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.TooManyRequestsException;
import com.siteflow.web.dto.AuthTokenView;
import com.siteflow.web.dto.CurrentUserView;
import com.siteflow.web.dto.LoginRequestDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import com.siteflow.security.ratelimit.RateLimitProperties;
import com.siteflow.security.ratelimit.RateLimitingFilter;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final RateLimitProperties rateLimitProperties;
    private final com.siteflow.service.AuditService auditService;
    private final com.siteflow.service.UserService userService;
    private final com.siteflow.security.AuthCookieService authCookieService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            RateLimitProperties rateLimitProperties,
            com.siteflow.service.AuditService auditService,
            com.siteflow.service.UserService userService,
            com.siteflow.security.AuthCookieService authCookieService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitProperties = rateLimitProperties;
        this.auditService = auditService;
        this.userService = userService;
        this.authCookieService = authCookieService;
    }

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            RateLimitProperties rateLimitProperties,
            com.siteflow.service.AuditService auditService,
            com.siteflow.service.UserService userService) {
        this(authenticationManager, jwtTokenProvider, loginAttemptService, rateLimitProperties, auditService, userService, null);
    }

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            RateLimitProperties rateLimitProperties,
            com.siteflow.service.AuditService auditService) {
        this(authenticationManager, jwtTokenProvider, loginAttemptService, rateLimitProperties, auditService, null, null);
    }

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            RateLimitProperties rateLimitProperties) {
        this(authenticationManager, jwtTokenProvider, loginAttemptService, rateLimitProperties, null, null, null);
    }

    /**
     * Authenticates user credentials and returns a stateless JWT access token.
     * Enforces rate limiting against brute-force password guessing attacks.
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenView> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        String clientIp = resolveClientIp(httpRequest);

        if (loginAttemptService.isBlocked(clientIp)) {
            log.warn("Blocked login attempt from IP {} due to excessive failed attempts", clientIp);
            throw new TooManyRequestsException("Too many failed login attempts. Please try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            loginAttemptService.loginSucceeded(clientIp);
            log.info("Successful authentication for user: {} from IP: {}", principal.getUsername(), clientIp);

            if (auditService != null) {
                auditService.recordAuthSuccess(principal.getUserId(), principal.getUsername(), clientIp);
            }

            String token = jwtTokenProvider.generateToken(principal);
            long expiresInSeconds = jwtTokenProvider.getExpirationMs() / 1000;

            if (authCookieService != null && httpResponse != null) {
                authCookieService.addAuthCookies(httpRequest, httpResponse, token, expiresInSeconds);
            }

            AuthTokenView tokenView = new AuthTokenView(
                    token,
                    "Bearer",
                    expiresInSeconds,
                    principal.getUserId(),
                    principal.getUsername(),
                    principal.getRoleName());

            return ApiResponse.success("Login successful.", tokenView);
        } catch (AuthenticationException ex) {
            loginAttemptService.loginFailed(clientIp);
            if (auditService != null) {
                auditService.recordAuthFailure(request.username(), clientIp, "Invalid credentials or inactive account");
            }
            log.warn("Failed authentication attempt for username: {} from IP: {}", request.username(), clientIp);
            throw ex;
        }
    }

    /**
     * Issues or refreshes a CSRF token and sets the XSRF-TOKEN cookie.
     */
    @GetMapping("/csrf")
    public ApiResponse<java.util.Map<String, String>> csrf(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String csrfToken = "";
        if (authCookieService != null && response != null) {
            csrfToken = authCookieService.addCsrfCookie(request, response, 3600L);
        }
        return ApiResponse.success("CSRF token.", java.util.Map.of("csrfToken", csrfToken != null ? csrfToken : ""));
    }

    /**
     * Identifies the authenticated caller, including role, from the current JWT security context.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CurrentUserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Current user.",
                new CurrentUserView(principal.getUserId(), principal.getUsername(), principal.getRoleName()));
    }

    /**
     * Invalidates all active tokens for the authenticated caller by incrementing their token version and clearing cookies.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        if (userService != null && principal != null && principal.getUserId() != null) {
            userService.revokeUserTokens(principal.getUserId());
        }
        if (authCookieService != null && httpResponse != null) {
            authCookieService.clearAuthCookies(httpRequest, httpResponse);
        }
        return ApiResponse.success("Successfully logged out. All active sessions have been invalidated.", null);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return RateLimitingFilter.resolveClientIp(request, rateLimitProperties.isTrustProxy());
    }
}
