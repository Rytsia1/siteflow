package com.siteflow.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class UserPrincipal extends User {

    private final Long userId;
    private final String roleName;
    private final Integer tokenVersion;

    public UserPrincipal(Long userId, String username, String passwordHash, String roleName) {
        this(userId, username, passwordHash, roleName, true, 1);
    }

    public UserPrincipal(Long userId, String username, String passwordHash, String roleName, boolean enabled) {
        this(userId, username, passwordHash, roleName, enabled, 1);
    }

    public UserPrincipal(Long userId, String username, String passwordHash, String roleName, boolean enabled, Integer tokenVersion) {
        super(username, passwordHash, enabled, true, true, true, authorities(roleName));
        this.userId = userId;
        this.roleName = roleName;
        this.tokenVersion = tokenVersion != null ? tokenVersion : 1;
    }

    private static List<GrantedAuthority> authorities(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        String authority = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return List.of(new SimpleGrantedAuthority(authority));
    }
}
