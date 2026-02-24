package com.netcompany.vat.coredomain;

/**
 * Lifecycle status of a {@link VatReturn}.
 */
public enum VatReturnStatus {

    /** Return is being prepared — not yet submitted. */
    DRAFT,

    /** Return has been submitted to the tax authority. */
    SUBMITTED,

    /** Tax authority has accepted the return. */
    ACCEPTED,

    /** Tax authority has rejected the return — corrections required. */
    REJECTED
}
