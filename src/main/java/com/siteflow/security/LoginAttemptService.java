package com.siteflow.security;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe, in-memory service that tracks failed login attempts by client identifier
 * (such as client IP) to protect against brute-force and credential stuffing attacks.
 *
 * <p>Enforces a sliding window lockout: after {@code maxAttempts} failed logins within
 * the lockout window, subsequent login requests are blocked until {@code lockoutDurationMs}
 * has elapsed or a successful authentication resets the counter.
 */
@Slf4j
@Service
public class LoginAttemptService {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long DEFAULT_LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes

    private final int maxAttempts;
    private final long lockoutDurationMs;
    private final ConcurrentHashMap<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCKOUT_DURATION_MS);
    }

    public LoginAttemptService(
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.lockout-duration-ms:900000}") long lockoutDurationMs) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationMs = lockoutDurationMs;
    }

    /**
     * Checks whether the client identifier is currently locked out due to repeated failed logins.
     */
    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        AttemptRecord record = attemptsCache.get(key);
        if (record == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - record.lastAttemptTimestamp();
        if (elapsed >= lockoutDurationMs) {
            attemptsCache.remove(key);
            return false;
        }

        return record.attempts() >= maxAttempts;
    }

    /**
     * Records a failed login attempt for the specified client identifier.
     */
    public void loginFailed(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        evictExpiredIfLarge();

        attemptsCache.compute(key, (k, record) -> {
            long now = System.currentTimeMillis();
            if (record == null || (now - record.lastAttemptTimestamp()) >= lockoutDurationMs) {
                return new AttemptRecord(1, now);
            }
            int newAttempts = record.attempts() + 1;
            if (newAttempts >= maxAttempts) {
                log.warn("Security alert: Client identifier {} reached {} failed login attempts. Temporarily locked out.",
                        key, newAttempts);
            }
            return new AttemptRecord(newAttempts, now);
        });
    }

    /**
     * Resets the failed attempt counter upon successful login.
     */
    public void loginSucceeded(String key) {
        if (key != null && !key.isBlank()) {
            attemptsCache.remove(key);
        }
    }

    /**
     * Resets all recorded attempts (useful for integration testing).
     */
    public void resetAll() {
        attemptsCache.clear();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getLockoutDurationMs() {
        return lockoutDurationMs;
    }

    private void evictExpiredIfLarge() {
        if (attemptsCache.size() > 1000) {
            long now = System.currentTimeMillis();
            attemptsCache.entrySet().removeIf(entry -> (now - entry.getValue().lastAttemptTimestamp()) >= lockoutDurationMs);
        }
    }

    private record AttemptRecord(int attempts, long lastAttemptTimestamp) {
    }
}
