package com.netcompany.vat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for registering a new counterparty.
 *
 * @param name        legal name of the entity
 * @param vatNumber   VAT registration number (format is jurisdiction-specific; optional)
 * @param countryCode ISO 3166-1 alpha-2 country code (e.g. "DK")
 */
public record CreateCounterpartyRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String vatNumber,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode
) {}
