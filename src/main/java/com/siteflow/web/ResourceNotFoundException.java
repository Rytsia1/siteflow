package com.siteflow.web;

/**
 * Thrown by services when a referenced entity (by id or other identifier) does not
 * exist, as distinct from {@link IllegalArgumentException} for malformed/invalid input
 * where the target does exist. Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
