package com.viame.exception;

/**
 * Thrown when no eligible (approved, online) driver is found within the dispatch radius.
 */
public class DriverUnavailableException extends RuntimeException {
    public DriverUnavailableException(String message) {
        super(message);
    }
}

