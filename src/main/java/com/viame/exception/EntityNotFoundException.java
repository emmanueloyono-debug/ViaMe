package com.viame.exception;

/**
 * Thrown when a referenced entity (user, trip, vehicle, pricing rule...) does not exist.
 */
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}

