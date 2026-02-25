package com.netcompany.vat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for opening a new VAT filing period.
 *
 * @param jurisdictionCode ISO country code of the jurisdiction (e.g. "DK")
 * @param periodStart      first day of the period (ISO 8601 date, e.g. "2026-01-01")
 * @param periodEnd        last day of the period (ISO 8601 date, e.g. "2026-03-31")
 * @param filingCadence    cadence value — one of: MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL
 */
public record OpenPeriodRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String jurisdictionCode,
        @NotBlank String periodStart,
        @NotBlank String periodEnd,
        @NotBlank String filingCadence
) {}
