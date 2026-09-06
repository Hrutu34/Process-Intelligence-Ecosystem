package com.pie.backend.exception;

public class InvalidProcessGraphException extends IllegalArgumentException {

    public InvalidProcessGraphException(String message) {
        super(message);
    }

    public InvalidProcessGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
