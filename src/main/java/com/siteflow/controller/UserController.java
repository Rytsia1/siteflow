package com.siteflow.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siteflow.security.UserPrincipal;
import com.siteflow.service.UserService;
import com.siteflow.web.ApiResponse;
import com.siteflow.web.dto.DeactivateUserRequestDto;
import com.siteflow.web.dto.DeactivatedUserView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Self-service account deactivation and privacy anonymization request.
     * Accessible by any authenticated user for their own account.
     */
    @PostMapping("/me/deactivate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DeactivatedUserView> deactivateOwnAccount(
            @RequestBody @Valid DeactivateUserRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        DeactivatedUserView view = userService.deactivateAndAnonymizeUser(principal.getUserId());
        return ApiResponse.success("Account successfully deactivated and personal data anonymized.", view);
    }

    /**
     * Administrative user deactivation and data anonymization.
     * Accessible only by administrators.
     */
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DeactivatedUserView> deactivateUserByAdmin(
            @PathVariable Long id,
            @RequestBody @Valid DeactivateUserRequestDto dto) {
        DeactivatedUserView view = userService.deactivateAndAnonymizeUser(id);
        return ApiResponse.success("User account successfully deactivated and anonymized.", view);
    }
}
