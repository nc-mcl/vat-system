package com.netcompany.vat.skatclient;

/**
 * Phase 1 PEPPOL e-invoice stub.
 *
 * <p>PEPPOL BIS 3.0 e-invoicing is a Phase 2 feature required for ViDA DRR compliance.
 * This stub exists to satisfy the {@link PeppolClient} interface contract and will throw
 * {@link UnsupportedOperationException} if called.
 *
 * <p><strong>Phase 2 upgrade:</strong> Replace this bean with a real PEPPOL BIS 3.0
 * implementation via NemHandel. Do NOT implement OIOUBL 2.1 — it is phased out May 15, 2026.
 */
public class PeppolClientStub implements PeppolClient {

    @Override
    public String sendInvoice(String invoiceXml) {
        throw new UnsupportedOperationException(
                "PEPPOL e-invoicing is a Phase 2 feature (ViDA DRR). "
                + "Implement PeppolClientImpl using PEPPOL BIS 3.0 when Phase 2 begins.");
    }
}
