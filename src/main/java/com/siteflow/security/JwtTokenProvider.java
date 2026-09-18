package com.siteflow.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT signing secret is not configured in environment or properties.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT signing secret must be at least 256 bits (32 bytes).");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserPrincipal principal) {
        return generateToken(principal, expirationMs);
    }

    /**
     * Generates a token with a custom expiration duration (supports positive or negative durations for testing).
     */
    public String generateToken(UserPrincipal principal, long customExpirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + customExpirationMs);
        Integer tokenVersion = principal.getTokenVersion() != null ? principal.getTokenVersion() : 1;

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("userId", principal.getUserId())
                .claim("role", principal.getRoleName())
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token presented");
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token presented: {}", ex.getClass().getSimpleName());
        }
        return false;
    }

    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        String username = claims.getSubject();
        Number userIdNumber = claims.get("userId", Number.class);
        Long userId = userIdNumber != null ? userIdNumber.longValue() : null;
        String role = claims.get("role", String.class);
        Integer tokenVersion = claims.get("tokenVersion", Integer.class);

        UserPrincipal principal = new UserPrincipal(userId, username, "", role, true, tokenVersion != null ? tokenVersion : 1);
        return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Integer getTokenVersion(Claims claims) {
        if (claims == null) {
            return null;
        }
        return claims.get("tokenVersion", Integer.class);
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
