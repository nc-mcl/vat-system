package com.netcompany.vat.api.dto;

/**
 * Response body for a counterparty.
 *
 * @param id          counterparty UUID
 * @param name        legal name
 * @param vatNumber   VAT registration number, or null if not registered
 * @param countryCode ISO 3166-1 alpha-2 country code
 */
public record CounterpartyResponse(
        String id,
        String name,
        String vatNumber,
        String countryCode
) {}
