package com.netcompany.vat.skatclient;

/**
 * Anti-corruption boundary for VIES (VAT Information Exchange System) VAT number validation.
 *
 * <p>Phase 1: Implemented by {@link ViesClientStub} — validates against a regex pattern.
 *
 * <p>Phase 2: Replace stub with {@code ViesClientImpl} using Spring WebClient
 * against the EU VIES REST API.
 */
public interface ViesClient {

    /**
     * Validates a VAT number against VIES.
     *
     * @param countryCode ISO 3166-1 alpha-2 country code (e.g. "DK")
     * @param vatNumber   the VAT number to validate (without the country prefix)
     * @return validation result including whether the number is registered and active
     * @throws ViesUnavailableException if the VIES service is unreachable
     */
    ViesValidationResult validateVatNumber(String countryCode, String vatNumber);
}
