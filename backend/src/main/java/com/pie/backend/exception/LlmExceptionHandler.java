package com.pie.backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized error handling for the VW LLMaaS REST API
 * ({@code /api/llm/**}). Ensures no secrets, tokens, or raw upstream bodies
 * ever leak into an HTTP response, and maps failures to sensible status codes.
 */
@RestControllerAdvice(basePackageClasses = com.pie.backend.controller.LlmController.class)
public class LlmExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LlmExceptionHandler.class);

    @ExceptionHandler(LlmaasAuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(LlmaasAuthException ex) {
        log.error("VW LLMaaS authentication error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "LLMAAS_AUTH_ERROR", ex.getMessage());
    }

    @ExceptionHandler(LlmaasApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(LlmaasApiException ex) {
        HttpStatusCode upstreamStatus = ex.getStatusCode();
        HttpStatus status = (upstreamStatus != null && upstreamStatus.is4xxClientError())
                ? HttpStatus.BAD_GATEWAY
                : HttpStatus.SERVICE_UNAVAILABLE;
        log.error("VW LLMaaS API error: {}", ex.getMessage());
        return build(status, "LLMAAS_API_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error in VW LLMaaS API", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
