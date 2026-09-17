package com.siteflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

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
    @DisplayName("NoResourceFoundException (Spring Boot 3.2+) maps to 404 Not Found")
    void noResourceFound_mapsTo404() {
        when(request.getRequestURI()).thenReturn("/api/nonexistent-route");

        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleNoResourceFound(new NoResourceFoundException(HttpMethod.GET, "/api/nonexistent-route"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo("error");
        assertThat(response.getBody().message()).isEqualTo("The requested endpoint does not exist.");
        assertThat(response.getBody().data().status()).isEqualTo(404);
    }

    @Test
    @DisplayName("NoHandlerFoundException maps to 404 Not Found")
    void noHandlerFound_mapsTo404() {
        when(request.getRequestURI()).thenReturn("/api/unknown");

        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<ApiResponse<ErrorDetails>> response =
                handler.handleNoHandlerFound(new NoHandlerFoundException("GET", "/api/unknown", headers), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("The requested endpoint does not exist.");
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
    @DisplayName("BusinessRuleViolationException maps to 409 Conflict")
    void businessRuleViolation_mapsTo409() {
        when(request.getRequestURI()).thenReturn("/api/procurement/material-requests/1/approve");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleBusinessRuleViolation(
                new BusinessRuleViolationException("Cannot approve request in REJECTED state"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Cannot approve request in REJECTED state");
        assertThat(response.getBody().data().status()).isEqualTo(409);
        assertThat(response.getBody().data().error()).isEqualTo("Conflict");
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
    @DisplayName("DataAccessException maps to 500 without leaking the underlying database connection or query details")
    void dataAccess_mapsTo500WithoutLeakingDetails() {
        when(request.getRequestURI()).thenReturn("/api/items");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleDataAccessException(
                new CannotAcquireLockException("Lock wait timeout exceeded; try restarting transaction: SELECT * FROM items FOR UPDATE"),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message())
                .isEqualTo("A database error occurred while processing the request.")
                .doesNotContain("Lock wait timeout")
                .doesNotContain("SELECT");
        assertThat(response.getBody().data().status()).isEqualTo(500);
        assertThat(response.getBody().data().error()).isEqualTo("Internal Server Error");
    }

    @Test
    @DisplayName("Malformed request body maps to 400")
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
    @DisplayName("MethodArgumentTypeMismatchException maps to 400 and names the parameter")
    void typeMismatch_mapsTo400() {
        when(request.getRequestURI()).thenReturn("/api/analytics/forecast/abc");
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "itemId", null, new IllegalArgumentException());

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleTypeMismatch(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("itemId");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException maps to 405 Method Not Allowed")
    void methodNotAllowed_mapsTo405() {
        when(request.getRequestURI()).thenReturn("/api/borrow-requests");
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleMethodNotAllowed(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().message()).isEqualTo("HTTP method not supported for this endpoint.");
        assertThat(response.getBody().data().status()).isEqualTo(405);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException maps to 415 Unsupported Media Type")
    void unsupportedMediaType_mapsTo415() {
        when(request.getRequestURI()).thenReturn("/api/borrow-requests");
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, Collections.singletonList(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleUnsupportedMediaType(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().message()).isEqualTo("Unsupported media type.");
        assertThat(response.getBody().data().status()).isEqualTo(415);
    }

    @Test
    @DisplayName("HttpMediaTypeNotAcceptableException maps to 406 Not Acceptable")
    void notAcceptable_mapsTo406() {
        when(request.getRequestURI()).thenReturn("/api/items");
        HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException("Not acceptable");

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleNotAcceptable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getBody().message()).isEqualTo("Requested media type is not acceptable.");
        assertThat(response.getBody().data().status()).isEqualTo(406);
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
    @DisplayName("Validation errors surface per-field and global messages in structured map")
    void validationErrors_surfaceStructuredFieldErrors() throws NoSuchMethodException {
        when(request.getRequestURI()).thenReturn("/api/procurement/material-requests");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "materialRequestDto");
        bindingResult.addError(new FieldError("materialRequestDto", "justification", "must not be blank"));
        bindingResult.addError(new ObjectError("materialRequestDto", "custom object validation failed"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed.");
        assertThat(response.getBody().data().fieldErrors())
                .containsEntry("justification", "must not be blank")
                .containsEntry("materialRequestDto", "custom object validation failed");
    }

    @Test
    @DisplayName("ConstraintViolationException maps to 400 Bad Request with field errors")
    void constraintViolation_mapsTo400WithFieldErrors() {
        when(request.getRequestURI()).thenReturn("/api/items");

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("recordAdjustment.qty");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be greater than 0");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        ResponseEntity<ApiResponse<ErrorDetails>> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed.");
        assertThat(response.getBody().data().fieldErrors()).containsEntry("qty", "must be greater than 0");
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
