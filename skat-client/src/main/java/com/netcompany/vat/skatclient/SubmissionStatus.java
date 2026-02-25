package com.netcompany.vat.skatclient;

/**
 * Status of a VAT return submission to the tax authority.
 */
public enum SubmissionStatus {

    /** Authority has accepted the return — no further action required. */
    ACCEPTED,

    /** Authority has rejected the return — corrections required before resubmission. */
    REJECTED,

    /** Return has been received and is awaiting authority processing. */
    PENDING
}
