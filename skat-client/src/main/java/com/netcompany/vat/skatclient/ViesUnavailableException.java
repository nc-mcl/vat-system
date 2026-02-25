package com.netcompany.vat.skatclient;

/**
 * Thrown when the VIES VAT number validation service is unavailable.
 *
 * <p>Callers should translate this to a 503 Service Unavailable response or
 * apply a fallback validation strategy.
 */
public class ViesUnavailableException extends RuntimeException {

    public ViesUnavailableException(String message) {
        super(message);
    }

    public ViesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
