package com.netcompany.vat.api.dto;

/**
 * Response body for a VAT filing period.
 *
 * <p>Dates are ISO 8601 strings (e.g. "2026-01-01").
 *
 * @param id               period UUID
 * @param jurisdictionCode jurisdiction code (e.g. "DK")
 * @param periodStart      start date of the period
 * @param periodEnd        end date of the period
 * @param filingCadence    cadence (MONTHLY / QUARTERLY / SEMI_ANNUAL / ANNUAL)
 * @param filingDeadline   last date on which a filing is accepted without penalty
 * @param status           lifecycle status (OPEN / FILED / ASSESSED / CORRECTED)
 */
public record TaxPeriodResponse(
        String id,
        String jurisdictionCode,
        String periodStart,
        String periodEnd,
        String filingCadence,
        String filingDeadline,
        String status
) {}
