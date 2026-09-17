package com.siteflow.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for server-side rate limiting and abuse prevention.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean trustProxy = false;
    private Policy login = new Policy(5, 60);
    private Policy api = new Policy(100, 60);
    private Policy sensitive = new Policy(30, 60);

    public RateLimitProperties() {
    }

    @Getter
    @Setter
    public static class Policy {
        private int requests;
        private long windowSeconds;

        public Policy() {
            this(100, 60);
        }

        public Policy(int requests, long windowSeconds) {
            this.requests = requests;
            this.windowSeconds = windowSeconds;
        }
    }
}
