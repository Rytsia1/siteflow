package com.siteflow.web.dto;

import java.time.LocalDateTime;

public record DeactivatedUserView(
        Long userId,
        String username,
        String anonymizedName,
        boolean isActive,
        LocalDateTime deactivatedAt) {
}
