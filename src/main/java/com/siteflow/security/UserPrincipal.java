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

    public UserPrincipal(Long userId, String username, String passwordHash, String roleName) {
        super(username, passwordHash, authorities(roleName));
        this.userId = userId;
        this.roleName = roleName;
    }

    private static List<GrantedAuthority> authorities(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        String authority = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return List.of(new SimpleGrantedAuthority(authority));
    }
}
