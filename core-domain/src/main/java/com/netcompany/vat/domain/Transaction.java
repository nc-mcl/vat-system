package com.netcompany.vat.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An economic event that affects VAT position within a period.
 *
 * <p>Immutable. Once persisted, a transaction is never modified — corrections
 * create new transactions linked to a corrected {@link VatReturn}.
 *
 * @param id               immutable system identifier
 * @param jurisdictionCode the jurisdiction whose rules govern this transaction
 * @param periodId         the {@link TaxPeriod} this transaction belongs to
 * @param counterpartyId   the supplier or customer involved
 * @param transactionDate  the tax point date (VAT liability date)
 * @param description      human-readable description
 * @param amountExclVat    the base taxable amount (excl. VAT), in øre
 * @param classification   the VAT classification applied to this transaction
 * @param createdAt        system clock timestamp at creation (UTC, immutable)
 */
public record Transaction(
        UUID id,
        JurisdictionCode jurisdictionCode,
        UUID periodId,
        UUID counterpartyId,
        LocalDate transactionDate,
        String description,
        MonetaryAmount amountExclVat,
        TaxClassification classification,
        Instant createdAt
) {

    /**
     * Computes the VAT amount for this transaction using exact integer arithmetic.
     *
     * <p>Formula: {@code vatAmount = amountExclVat * rateInBasisPoints / 10_000}
     * Result is truncated (floor division) — rounding is always in favour of the taxpayer.
     *
     * <p>Returns {@link MonetaryAmount#ZERO} when the rate is not applicable
     * (i.e. {@code rateInBasisPoints < 0}, used for EXEMPT and OUT_OF_SCOPE transactions).
     */
    public MonetaryAmount vatAmount() {
        if (classification.rateInBasisPoints() < 0) {
            return MonetaryAmount.ZERO;
        }
        long vatOere = (amountExclVat.oere() * classification.rateInBasisPoints()) / 10_000L;
        return MonetaryAmount.ofOere(vatOere);
    }
}
