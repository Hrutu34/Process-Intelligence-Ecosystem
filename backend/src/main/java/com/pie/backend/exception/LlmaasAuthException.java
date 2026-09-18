package com.pie.backend.exception;

/**
 * Raised when the VW Cloud IDP token endpoint cannot be reached, rejects the
 * client credentials, or returns an unusable response. Never carries the
 * client secret or any token value in its message.
 */
public class LlmaasAuthException extends RuntimeException {

    public LlmaasAuthException(String message) {
        super(message);
    }

    public LlmaasAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
