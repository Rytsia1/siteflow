package com.siteflow.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthCookieService {

    public static final String DEFAULT_AUTH_COOKIE_NAME = "siteflow_token";
    public static final String DEFAULT_CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    public static final String CSRF_HEADER_FALLBACK = "X-CSRF-TOKEN";

    private final String authCookieName;
    private final String csrfCookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookiePath;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthCookieService(
            @Value("${jwt.cookie.name:siteflow_token}") String authCookieName,
            @Value("${jwt.cookie.csrf-name:XSRF-TOKEN}") String csrfCookieName,
            @Value("${jwt.cookie.secure:false}") boolean cookieSecure,
            @Value("${jwt.cookie.same-site:Lax}") String cookieSameSite,
            @Value("${jwt.cookie.path:/}") String cookiePath) {
        this.authCookieName = authCookieName;
        this.csrfCookieName = csrfCookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookiePath = cookiePath;
    }

    /**
     * Attaches an HttpOnly, Secure, SameSite JWT authentication cookie and a readable CSRF cookie.
     */
    public String addAuthCookies(HttpServletRequest request, HttpServletResponse response, String jwtToken, long maxAgeSeconds) {
        boolean isSecure = resolveIsSecure(request);

        ResponseCookie authCookie = ResponseCookie.from(authCookieName, jwtToken)
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());

        Cookie servletAuthCookie = new Cookie(authCookieName, jwtToken);
        servletAuthCookie.setHttpOnly(true);
        servletAuthCookie.setSecure(isSecure);
        servletAuthCookie.setPath(cookiePath);
        servletAuthCookie.setMaxAge((int) Math.min(maxAgeSeconds, Integer.MAX_VALUE));
        response.addCookie(servletAuthCookie);

        String csrfToken = generateCsrfToken();
        ResponseCookie csrfCookie = ResponseCookie.from(csrfCookieName, csrfToken)
                .httpOnly(false) // Accessible to browser JavaScript for Double Submit CSRF
                .secure(isSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());

        Cookie servletCsrfCookie = new Cookie(csrfCookieName, csrfToken);
        servletCsrfCookie.setHttpOnly(false);
        servletCsrfCookie.setSecure(isSecure);
        servletCsrfCookie.setPath(cookiePath);
        servletCsrfCookie.setMaxAge((int) Math.min(maxAgeSeconds, Integer.MAX_VALUE));
        response.addCookie(servletCsrfCookie);

        return csrfToken;
    }

    /**
     * Attaches or refreshes a standalone CSRF cookie (e.g. for initial page visit).
     */
    public String addCsrfCookie(HttpServletRequest request, HttpServletResponse response, long maxAgeSeconds) {
        boolean isSecure = resolveIsSecure(request);
        String csrfToken = generateCsrfToken();
        ResponseCookie csrfCookie = ResponseCookie.from(csrfCookieName, csrfToken)
                .httpOnly(false)
                .secure(isSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());

        Cookie servletCsrfCookie = new Cookie(csrfCookieName, csrfToken);
        servletCsrfCookie.setHttpOnly(false);
        servletCsrfCookie.setSecure(isSecure);
        servletCsrfCookie.setPath(cookiePath);
        servletCsrfCookie.setMaxAge((int) Math.min(maxAgeSeconds, Integer.MAX_VALUE));
        response.addCookie(servletCsrfCookie);

        return csrfToken;
    }

    /**
     * Clears authentication and CSRF cookies by setting Max-Age to 0.
     */
    public void clearAuthCookies(HttpServletRequest request, HttpServletResponse response) {
        boolean isSecure = resolveIsSecure(request);

        ResponseCookie authCookie = ResponseCookie.from(authCookieName, "")
                .httpOnly(true)
                .secure(isSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, authCookie.toString());

        Cookie servletAuth = new Cookie(authCookieName, "");
        servletAuth.setHttpOnly(true);
        servletAuth.setSecure(isSecure);
        servletAuth.setPath(cookiePath);
        servletAuth.setMaxAge(0);
        response.addCookie(servletAuth);

        ResponseCookie csrfCookie = ResponseCookie.from(csrfCookieName, "")
                .httpOnly(false)
                .secure(isSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());

        Cookie servletCsrf = new Cookie(csrfCookieName, "");
        servletCsrf.setHttpOnly(false);
        servletCsrf.setSecure(isSecure);
        servletCsrf.setPath(cookiePath);
        servletCsrf.setMaxAge(0);
        response.addCookie(servletCsrf);
    }

    /**
     * Extracts JWT token from the authentication cookie, if present.
     */
    public String extractAuthToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (authCookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extracts CSRF token from the CSRF cookie, if present.
     */
    public String extractCsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (csrfCookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public String getAuthCookieName() {
        return authCookieName;
    }

    public String getCsrfCookieName() {
        return csrfCookieName;
    }

    private String generateCsrfToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private boolean resolveIsSecure(HttpServletRequest request) {
        if (cookieSecure) {
            return true;
        }
        if (request != null) {
            if (request.isSecure()) {
                return true;
            }
            String proto = request.getHeader("X-Forwarded-Proto");
            return "https".equalsIgnoreCase(proto);
        }
        return false;
    }
}
