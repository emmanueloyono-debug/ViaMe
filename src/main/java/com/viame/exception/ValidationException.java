package com.viame.exception;

/**
 * Thrown when user-supplied input violates a business or format rule
 * (e.g. malformed email, duplicate phone number, out-of-range surge multiplier).
 * Controllers should display the message directly to the user.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}

