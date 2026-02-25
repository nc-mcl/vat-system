package com.netcompany.vat.skatclient;

import java.time.Instant;

/**
 * Result of submitting a VAT return to SKAT (or a simulated SKAT stub).
 *
 * @param skatReference authority-assigned reference number; present when ACCEPTED
 * @param status        acceptance status from the authority
 * @param message       human-readable explanation (error details when REJECTED, confirmation when ACCEPTED)
 * @param processedAt   timestamp when the authority processed the submission
 */
public record SkatSubmissionResult(
        String skatReference,
        SubmissionStatus status,
        String message,
        Instant processedAt
) {}
