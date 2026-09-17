package com.siteflow.web;

/**
 * Thrown by services and domain components when an operation violates a business invariant
 * or workflow rule. Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
