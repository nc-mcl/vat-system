package com.netcompany.vat.domain;

/**
 * Jurisdiction-neutral VAT treatment codes. The concrete rate for each code
 * is provided by the {@link com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin}
 * for the relevant jurisdiction.
 */
public enum TaxCode {

    /** Standard rate — e.g. 25% in Denmark. */
    STANDARD,

    /** Zero-rated supply — VAT-registered, zero rate applies (e.g. exports, intra-EU goods). */
    ZERO_RATED,

    /** Exempt — no VAT charged, no input VAT recovery (e.g. healthcare, education). */
    EXEMPT,

    /**
     * Reverse charge — buyer accounts for VAT. Applies to cross-border B2B services.
     * The supplier invoices without VAT; the buyer self-assesses both output and input VAT.
     */
    REVERSE_CHARGE,

    /** Out of scope — not subject to VAT at all (e.g. wages, dividends). */
    OUT_OF_SCOPE
}
