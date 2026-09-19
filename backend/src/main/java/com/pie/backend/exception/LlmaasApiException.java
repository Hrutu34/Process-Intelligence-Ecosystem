package com.pie.backend.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Raised when the VW LLMaaS API itself returns an error (4xx/5xx) or an
 * unexpected/empty payload for a chat completion or embedding request.
 */
public class LlmaasApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public LlmaasApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public LlmaasApiException(String message, HttpStatusCode statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
