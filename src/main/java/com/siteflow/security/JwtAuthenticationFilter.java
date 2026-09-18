package com.siteflow.security;

import java.io.IOException;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.siteflow.domain.UserWithRole;
import com.siteflow.mapper.UserMapper;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserMapper userMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        // Fail-safe: Unauthenticated requests bypass token validation and perform zero database queries
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = validateAndBuildAuthentication(token);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private Authentication validateAndBuildAuthentication(String token) {
        try {
            Claims claims = jwtTokenProvider.getClaims(token);
            String username = claims.getSubject();
            Number userIdNumber = claims.get("userId", Number.class);
            Long userId = userIdNumber != null ? userIdNumber.longValue() : null;
            Integer tokenVersion = claims.get("tokenVersion", Integer.class);
            String tokenRole = claims.get("role", String.class);

            if (userId == null && (username == null || username.isBlank())) {
                log.warn("JWT missing both userId and subject claims");
                return null;
            }

            // Verify current account state in database (single indexed lookup)
            UserWithRole user = null;
            if (userId != null) {
                user = userMapper.findUserWithRoleById(userId);
            }
            if (user == null && username != null && !username.isBlank()) {
                user = userMapper.findByUsername(username);
            }

            if (user == null) {
                log.warn("Authentication rejected: account not found in database for userId={}, username={}", userId, username);
                return null;
            }

            // 1. Account state: Inactive or deactivated accounts must not authenticate
            if (Boolean.FALSE.equals(user.getIsActive())) {
                log.warn("Authentication rejected: user account is deactivated (userId={}, username={})", user.getId(), user.getUsername());
                return null;
            }

            // 2. Token revocation: Version mismatch rejects previously issued tokens
            int dbTokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
            if (tokenVersion == null || !Objects.equals(dbTokenVersion, tokenVersion)) {
                log.warn("Authentication rejected: token version mismatch for user '{}' (tokenVer={}, dbVer={})",
                        user.getUsername(), tokenVersion, dbTokenVersion);
                return null;
            }

            // 3. Security state: Role in JWT must not contradict database state
            String normTokenRole = tokenRole != null ? (tokenRole.startsWith("ROLE_") ? tokenRole.substring(5) : tokenRole) : null;
            String normDbRole = user.getRoleName() != null ? (user.getRoleName().startsWith("ROLE_") ? user.getRoleName().substring(5) : user.getRoleName()) : null;
            if (normTokenRole != null && !normTokenRole.isBlank() && !normTokenRole.equalsIgnoreCase(normDbRole)) {
                log.warn("Authentication rejected: role claim in JWT ('{}') contradicts database role ('{}') for user '{}'",
                        tokenRole, user.getRoleName(), user.getUsername());
                return null;
            }

            // Construct UserPrincipal with authoritative identity and role from current database state
            UserPrincipal principal = new UserPrincipal(
                    user.getId(),
                    user.getUsername(),
                    "",
                    user.getRoleName(),
                    user.getIsActive(),
                    dbTokenVersion
            );

            return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
        } catch (Exception ex) {
            log.warn("Failed to validate JWT against account state: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
