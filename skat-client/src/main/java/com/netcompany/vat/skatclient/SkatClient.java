package com.netcompany.vat.skatclient;

import com.netcompany.vat.domain.VatReturn;

/**
 * Anti-corruption boundary for SKAT (Skattestyrelsen) VAT return submissions.
 *
 * <p>Phase 1: Implemented by {@link SkatClientStub} — simulates realistic SKAT responses
 * without connecting to the real API.
 *
 * <p>Phase 2: Replace stub with {@code SkatClientImpl} using Spring WebClient
 * against {@code https://api-sandbox.skat.dk} (then production).
 *
 * <p>No business logic lives in this interface or its implementations.
 * All domain decisions are made in the tax-engine or API layers.
 */
public interface SkatClient {

    /**
     * Submits a VAT return to SKAT.
     *
     * @param vatReturn the fully assembled VAT return to submit
     * @return the submission result with SKAT reference and acceptance status
     * @throws SkatUnavailableException if SKAT is unreachable or returns an unrecoverable error
     */
    SkatSubmissionResult submitReturn(VatReturn vatReturn);

    /**
     * Queries the current processing status of a previously submitted return.
     *
     * @param skatReference the SKAT reference number from a prior {@link #submitReturn} call
     * @return the current status result
     * @throws SkatUnavailableException if SKAT is unreachable
     */
    SkatSubmissionResult getSubmissionStatus(String skatReference);
}
