package com.siteflow.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.security.UserPrincipal;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.CurrentUserView;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Identifies the authenticated caller, including role, so the SPA can
     * gate admin-only views without a dedicated login endpoint (auth is
     * Basic-auth per request; there is no session to inspect otherwise).
     */
    @GetMapping("/me")
    public ApiResponse<CurrentUserView> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Current user.",
                new CurrentUserView(principal.getUserId(), principal.getUsername(), principal.getRoleName()));
    }
}
