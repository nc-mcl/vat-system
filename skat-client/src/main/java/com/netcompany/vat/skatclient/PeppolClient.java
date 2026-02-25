package com.netcompany.vat.skatclient;

/**
 * Anti-corruption boundary for PEPPOL BIS 3.0 e-invoice exchange.
 *
 * <p>Phase 1: Implemented by {@link PeppolClientStub} — throws
 * {@link UnsupportedOperationException} to signal Phase 2 scope.
 *
 * <p>Phase 2 (ViDA DRR): Replace stub with a real NemHandel/PEPPOL implementation.
 * Use PEPPOL BIS 3.0 only — OIOUBL 2.1 is phased out as of May 15, 2026.
 */
public interface PeppolClient {

    /**
     * Sends an e-invoice via the PEPPOL network.
     *
     * @param invoiceXml the PEPPOL BIS 3.0 XML invoice payload
     * @return the PEPPOL transmission ID
     */
    String sendInvoice(String invoiceXml);
}
