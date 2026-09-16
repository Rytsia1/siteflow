package com.siteflow.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import lombok.Getter;

@Getter
public class UserPrincipal extends User {

    private final Long userId;

    public UserPrincipal(Long userId, String username, String passwordHash, String roleName) {
        super(username, passwordHash, authorities(roleName));
        this.userId = userId;
    }

    private static List<GrantedAuthority> authorities(String roleName) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName));
    }
}
