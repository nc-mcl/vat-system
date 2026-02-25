package com.netcompany.vat.api.exception;

/**
 * Thrown when a requested entity does not exist in the system.
 * Maps to HTTP 404 Not Found via {@link com.netcompany.vat.api.handler.GlobalExceptionHandler}.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
