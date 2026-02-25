package com.netcompany.vat.skatclient.dto;

/**
 * VIES VAT number validation request payload.
 *
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g. "DK")
 * @param vatNumber   the VAT number to validate (without the country code prefix)
 */
public record ViesValidationRequest(
        String countryCode,
        String vatNumber
) {}
