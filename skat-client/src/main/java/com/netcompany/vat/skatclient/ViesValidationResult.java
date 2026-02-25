package com.netcompany.vat.skatclient;

/**
 * Result of a VIES VAT number validation.
 *
 * @param valid       whether the VAT number is registered and valid in VIES
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g. "DK")
 * @param vatNumber   the VAT number that was validated (without country prefix)
 * @param message     additional details (e.g. registered name when valid, error when invalid)
 */
public record ViesValidationResult(
        boolean valid,
        String countryCode,
        String vatNumber,
        String message
) {}
