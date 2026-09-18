package com.siteflow.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    private Long id;
    private Long roleId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String jobPosition;
    private Boolean isActive;
    private LocalDateTime deactivatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
