package com.siteflow.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWithRole {
    private Long id;
    private String username;
    private String passwordHash;
    private String roleName;
    private Boolean isActive;
    private Integer tokenVersion;
}
