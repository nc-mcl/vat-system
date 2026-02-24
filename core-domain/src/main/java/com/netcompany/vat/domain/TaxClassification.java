package com.netcompany.vat.domain;

import java.time.LocalDate;

/**
 * The VAT classification applied to a transaction line.
 *
 * <p>Immutable — determined at transaction time and never mutated.
 * The rate in basis points is stored alongside the code so that historical
 * transactions retain their rate even if the jurisdiction changes it later.
 *
 * @param taxCode            the jurisdiction-neutral VAT treatment
 * @param rateInBasisPoints  the applicable rate at classification time
 *                           (e.g. 2500 = 25.00%); -1 for exempt (not applicable)
 * @param isReverseCharge    true if the buyer accounts for VAT (self-assessment)
 * @param effectiveDate      the date on which this classification was determined
 */
public record TaxClassification(
        TaxCode taxCode,
        long rateInBasisPoints,
        boolean isReverseCharge,
        LocalDate effectiveDate
) {

    /**
     * Returns the VAT multiplier as a fraction.
     * For example, 2500 basis points → 0.25 as a rational (25/100).
     * Use only for display; all computation stays in øre.
     */
    public double rateAsDecimal() {
        return rateInBasisPoints / 10_000.0;
    }
}
