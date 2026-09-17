package com.siteflow.web;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Carried as the {@code data} payload of an error {@link ApiResponse}. Kept inside the
 * project's existing envelope (rather than adopting the flat
 * {timestamp, status, error, message, path} shape some frameworks use at the top level)
 * so every response — success or error — has the same {status, message, data} outline.
 * Includes requestId for correlating client-side failures with backend logs.
 */
public record ErrorDetails(LocalDateTime timestamp, int status, String error, String path,
                            Map<String, String> fieldErrors, String requestId) {

    public ErrorDetails(LocalDateTime timestamp, int status, String error, String path,
                        Map<String, String> fieldErrors) {
        this(timestamp, status, error, path, fieldErrors, null);
    }
}
