package com.siteflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException maps to 404 with the exception's own message")
    void resourceNotFound_mapsTo404() {
        when(request.getRequestURI()).thenReturn("/api/borrow-requests/999");

        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleNotFound(new ResourceNotFoundException("Borrow request not found: 999"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).isEqualTo("Borrow request not found: 999");
        assertThat(response.getBody().data().status()).isEqualTo(404);
        assertThat(response.getBody().data().error()).isEqualTo("Not Found");
        assertThat(response.getBody().data().path()).isEqualTo("/api/borrow-requests/999");
        assertThat(response.getBody().data().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("IllegalArgumentException (bad input) maps to 400")
    void illegalArgument_mapsTo400() {
        when(request.getRequestURI()).thenReturn("/api/procurement/material-requests");

        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleBadRequest(new IllegalArgumentException("Requested quantity must be positive"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Requested quantity must be positive");
    }

    @Test
    @DisplayName("IllegalStateException (invalid transition / insufficient stock / business rule) maps to 409")
    void illegalState_mapsTo409() {
        when(request.getRequestURI()).thenReturn("/api/borrow-requests");

        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleConflict(new IllegalStateException("Insufficient stock for item 5"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Insufficient stock for item 5");
    }

    @Test
    @DisplayName("AccessDeniedException (authorization failure) maps to 403 with a fixed, generic message")
    void accessDenied_mapsTo403() {
        when(request.getRequestURI()).thenReturn("/api/analytics/summary");

        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleAccessDenied(new AccessDeniedException("not admin"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Access denied.");
    }

    @Test
    @DisplayName("DataIntegrityViolationException maps to 409 without leaking the underlying SQL/constraint detail")
    void dataIntegrity_mapsTo409WithoutLeakingDetails() {
        when(request.getRequestURI()).thenReturn("/api/items");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("Duplicate entry 'DRL-001' for key 'uq_items_item_code'"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("The request could not be completed due to a data conflict.")
                .doesNotContain("uq_items_item_code");
    }

    @Test
    @DisplayName("Malformed request body maps to 400, not the old 500 (it used to fall through to the catch-all)")
    void malformedBody_mapsTo400NotServerError() {
        when(request.getRequestURI()).thenReturn("/api/borrow-requests");
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: unexpected token", (HttpInputMessage) null);

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleMalformedRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Malformed request body.");
    }

    @Test
    @DisplayName("Missing required query parameter maps to 400 and names the parameter")
    void missingParameter_mapsTo400() {
        when(request.getRequestURI()).thenReturn("/api/analytics/trends");
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("startDate", "LocalDate");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleMissingParameter(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("startDate");
    }

    @Test
    @DisplayName("Unexpected exceptions map to 500 with a generic message — the exception's own message is never returned")
    void unexpectedException_mapsTo500WithoutLeakingDetail() {
        when(request.getRequestURI()).thenReturn("/api/items");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleUnexpected(
                new RuntimeException("Connection refused: jdbc:mysql://prod-db:3306/siteflow"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("An unexpected error occurred.")
                .doesNotContain("jdbc:mysql");
    }

    @Test
    @DisplayName("Validation errors surface per-field messages in a structured map, not a stringified Java Map")
    void validationErrors_surfaceStructuredFieldErrors() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn("/api/procurement/material-requests");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "materialRequestDto");
        bindingResult.addError(new FieldError("materialRequestDto", "justification", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed.");
        assertThat(response.getBody().data().fieldErrors()).containsEntry("justification", "must not be blank");
    }

    /** MethodArgumentNotValidException requires a real MethodParameter; this dummy target supplies one. */
    private static MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
    }
}
