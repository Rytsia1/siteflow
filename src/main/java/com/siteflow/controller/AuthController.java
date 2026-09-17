package com.siteflow.controller;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.security.JwtTokenProvider;
import com.siteflow.security.UserPrincipal;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.AuthTokenView;
import com.siteflow.web.dto.CurrentUserView;
import com.siteflow.web.dto.LoginRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Authenticates user credentials and returns a stateless JWT access token.
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenView> login(@Valid @RequestBody LoginRequestDto request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
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
    }

    /**
     * Identifies the authenticated caller, including role, from the current JWT security context.
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Current user.",
                new CurrentUserView(principal.getUserId(), principal.getUsername(), principal.getRoleName()));
    }
}
