package com.netcompany.vat.api.reporting;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Phase 2 stub: SAF-T (Standard Audit File for Tax) export.
 *
 * <p>SAF-T export is planned for Phase 2 (ViDA compliance scope). The Danish SAF-T
 * standard (SAF-T Financial, version DK 1.0) requires structured XML export of the
 * general ledger and VAT transactions, compatible with SKAT digital audit requirements.
 *
 * <p>All methods throw {@link UnsupportedOperationException} until Phase 2 is implemented.
 */
@Service
public class SaftReportingService {

    /**
     * Generates a SAF-T XML export for the given VAT return period.
     *
     * @param returnId the VAT return to export
     * @throws UnsupportedOperationException always — SAF-T export is Phase 2 scope
     */
    public byte[] generateSaftXml(UUID returnId) {
        throw new UnsupportedOperationException(
                "SAF-T export is not available in Phase 1. " +
                "This feature is planned for Phase 2 (ViDA compliance scope). " +
                "SAF-T Financial (DK 1.0) generation requires structured ledger data " +
                "beyond the current Transaction model. Reference: returnId=" + returnId);
    }
}
