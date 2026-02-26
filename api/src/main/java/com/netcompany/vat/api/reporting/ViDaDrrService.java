package com.netcompany.vat.api.reporting;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Phase 2 stub: ViDA Digital Reporting Requirements (DRR) submission.
 *
 * <p>ViDA DRR requires real-time or near-real-time transaction reporting to the
 * tax authority, mandated under EU Directive 2022/701 (ViDA) with an implementation
 * deadline of 2028. Denmark must implement DRR as part of its ViDA obligations.
 *
 * <p>All methods throw {@link UnsupportedOperationException} until Phase 2 is implemented.
 *
 * @see <a href="https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52022PC0701">ViDA Directive</a>
 */
@Service
public class ViDaDrrService {

    /**
     * Submits a ViDA Digital Reporting Requirements (DRR) payload to the authority.
     *
     * @param returnId the VAT return to submit via DRR
     * @throws UnsupportedOperationException always — DRR is Phase 2 scope
     */
    public void submitDrr(UUID returnId) {
        throw new UnsupportedOperationException(
                "ViDA Digital Reporting Requirements (DRR) are not available in Phase 1. " +
                "This feature is planned for Phase 2 (ViDA 2028 deadline). " +
                "DRR requires real-time transaction-level reporting beyond the current " +
                "batch VAT return model. Reference: returnId=" + returnId);
    }

    /**
     * Generates an EU-salgsangivelse (EC Sales List) submission for Rubrik B goods transactions.
     *
     * <p>The EU-salgsangivelse cross-references {@code rubrikBGoodsEuSaleValue} on the
     * momsangivelse and must list individual EU customers with their VAT numbers and
     * transaction values. This requires counterparty VAT number validation via VIES.
     *
     * @param returnId the VAT return to generate the EU sales list for
     * @throws UnsupportedOperationException always — EU-salgsangivelse is Phase 2 scope
     */
    public void generateEuSalgsangivelse(UUID returnId) {
        throw new UnsupportedOperationException(
                "EU-salgsangivelse (EC Sales List) generation is not available in Phase 1. " +
                "This feature is planned for Phase 2 and requires VIES VAT number validation " +
                "for all Rubrik B goods counterparties. Reference: returnId=" + returnId);
    }
}
