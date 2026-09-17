package com.siteflow.security.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe sliding-window rate limiter.
 * Tracks timestamps of requests within a sliding time window per key.
 */
@Slf4j
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    public record RateLimitResult(boolean allowed, long retryAfterSeconds, int remainingRequests) {
    }

    /**
     * Checks if a request for the given key is permitted within the sliding window.
     *
     * @param key           identifier for the rate limit bucket (e.g. "login:ip:1.2.3.4" or "api:user:admin")
     * @param maxRequests   maximum number of requests permitted in the window
     * @param windowSeconds length of the sliding window in seconds
     * @return RateLimitResult indicating whether the request is allowed and retry details if rejected
     */
    public RateLimitResult tryConsume(String key, int maxRequests, long windowSeconds) {
        if (key == null || key.isBlank() || maxRequests <= 0 || windowSeconds <= 0) {
            return new RateLimitResult(true, 0, Math.max(0, maxRequests));
        }

        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        long threshold = now - windowMs;

        Deque<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            // Remove timestamps outside the sliding window
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= threshold) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                int remaining = Math.max(0, maxRequests - timestamps.size());
                return new RateLimitResult(true, 0, remaining);
            } else {
                long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
                long retryAfterMs = (oldest + windowMs) - now;
                long retryAfterSeconds = Math.max(1, (long) Math.ceil(retryAfterMs / 1000.0));
                log.warn("Rate limit exceeded for key '{}'. Limit: {} req / {}s. Retry after: {}s",
                        key, maxRequests, windowSeconds, retryAfterSeconds);
                return new RateLimitResult(false, retryAfterSeconds, 0);
            }
        }
    }

    /**
     * Resets rate limit for a specific key.
     */
    public void reset(String key) {
        if (key != null) {
            requestLogs.remove(key);
        }
    }

    /**
     * Resets all rate limit tracking (useful for test isolation).
     */
    public void resetAll() {
        requestLogs.clear();
    }

    /**
     * Periodically cleans up keys that have had no activity for more than 5 minutes.
     */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        requestLogs.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                return deque.isEmpty() || (now - deque.peekLast()) > 300_000L;
            }
        });
    }
}
