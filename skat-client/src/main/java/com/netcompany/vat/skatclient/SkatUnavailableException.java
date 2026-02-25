package com.netcompany.vat.skatclient;

/**
 * Thrown when the SKAT API is unavailable or returns an unrecoverable error.
 *
 * <p>Callers should translate this to a 503 Service Unavailable response.
 */
public class SkatUnavailableException extends RuntimeException {

    public SkatUnavailableException(String message) {
        super(message);
    }

    public SkatUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
