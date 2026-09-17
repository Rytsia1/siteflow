package com.siteflow.web;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Single place every controller's exceptions are translated into HTTP responses. Every
 * response here uses the same {@link ApiResponse} envelope the rest of the API returns
 * on success, with an {@link ErrorDetails} payload carrying the timestamp/status/error
 * reason/request path a client can use for logging or display — never a stack trace or
 * raw exception/DB detail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failures on @Valid request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fieldErrors.put(ge.getObjectName(), ge.getDefaultMessage()));
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed.", request, fieldErrors);
    }

    /** Validation failures on controller method parameters (e.g. @PathVariable, @RequestParam). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (var result : ex.getValueResults()) {
            String paramName = result.getMethodParameter().getParameterName();
            for (var error : result.getResolvableErrors()) {
                fieldErrors.put(paramName != null ? paramName : "parameter", error.getDefaultMessage());
            }
        }
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed.", request, fieldErrors);
    }

    /** Jakarta Bean Validation constraint violations. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(cv -> {
            String prop = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "parameter";
            if (prop.contains(".")) {
                prop = prop.substring(prop.lastIndexOf('.') + 1);
            }
            fieldErrors.put(prop, cv.getMessage());
        });
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed.", request, fieldErrors);
    }

    /** A referenced entity (by id or other identifier) does not exist. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    /** Unmapped endpoint or static resource requested. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "The requested endpoint does not exist.", request);
    }

    /** Requires spring.mvc.throw-exception-if-no-handler-found + spring.web.resources.add-mappings=false */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "The requested endpoint does not exist.", request);
    }

    /** Malformed input that isn't a Bean Validation failure — bad request shape/values. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Invalid state transitions, insufficient stock, and other business rule violations. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleConflict(
            IllegalStateException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Dedicated domain business rule violations. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleBusinessRuleViolation(
            BusinessRuleViolationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Database unique constraint or foreign key violations — masked to prevent SQL/table leakage. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildError(HttpStatus.CONFLICT, "The request could not be completed due to a data conflict.", request);
    }

    /** Other unexpected database exceptions — logged server-side, masked to avoid leaking DB internals. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {
        log.error("Database access error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred while processing the request.", request);
    }

    /** @PreAuthorize denials reaching here (thrown inside a controller method invocation). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Access denied.", request);
    }

    /** Controller-level authentication failure (e.g. invalid username/password during login). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid username or password.", request);
    }

    /** Rate limiting / repeated authentication failure lockout. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleTooManyRequests(
            TooManyRequestsException ex, HttpServletRequest request) {
        return buildError(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    /** Unparseable/malformed @RequestBody JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleMalformedRequest(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Missing required parameter: " + ex.getParameterName(), request);
    }

    /** e.g. a non-numeric path variable, or a query param value that doesn't match its enum. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + ex.getName(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not supported for this endpoint.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_ACCEPTABLE, "Requested media type is not acceptable.", request);
    }

    /** Anything not handled above. Logged with the full stack trace server-side; the
     *  client only ever receives a generic message — never the exception detail. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDetails>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiResponse<ErrorDetails>> buildError(
            HttpStatus status, String message, HttpServletRequest request) {
        return buildError(status, message, request, null);
    }

    private ResponseEntity<ApiResponse<ErrorDetails>> buildError(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        ErrorDetails details = new ErrorDetails(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(ApiResponse.error(message, details));
    }
}
