package com.netcompany.vat.domain;

/**
 * Lifecycle status of a {@link TaxPeriod}.
 */
public enum TaxPeriodStatus {

    /** Period is open — transactions may still be assigned. */
    OPEN,

    /** Period has been filed with the tax authority. */
    FILED,

    /** Tax authority has assessed the filing (accepted or adjusted). */
    ASSESSED,

    /** A correction has been submitted for this period. */
    CORRECTED
}
