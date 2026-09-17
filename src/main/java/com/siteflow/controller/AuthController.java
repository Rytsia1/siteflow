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

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            LoginAttemptService loginAttemptService,
            RateLimitProperties rateLimitProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttemptService = loginAttemptService;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * Authenticates user credentials and returns a stateless JWT access token.
     * Enforces rate limiting against brute-force password guessing attacks.
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenView> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest) {
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

            String token = jwtTokenProvider.generateToken(principal);
            long expiresInSeconds = jwtTokenProvider.getExpirationMs() / 1000;

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
            log.warn("Failed authentication attempt for username: {} from IP: {}", request.username(), clientIp);
            throw ex;
        }
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

    private String resolveClientIp(HttpServletRequest request) {
        return RateLimitingFilter.resolveClientIp(request, rateLimitProperties.isTrustProxy());
    }
}
