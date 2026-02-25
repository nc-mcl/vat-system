package com.netcompany.vat.api.exception;

/**
 * Thrown when an operation is attempted on a period or return that is in an
 * incompatible state (e.g. adding transactions to a FILED period, or assembling
 * a return that already exists).
 *
 * <p>Maps to HTTP 409 Conflict via {@link com.netcompany.vat.api.handler.GlobalExceptionHandler}.
 */
public class InvalidPeriodStateException extends RuntimeException {

    public InvalidPeriodStateException(String message) {
        super(message);
    }
}
