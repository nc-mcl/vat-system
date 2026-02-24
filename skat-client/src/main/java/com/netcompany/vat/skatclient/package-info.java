/**
 * SKAT client module — anti-corruption layer for all external authority integrations.
 *
 * <p>Adapts external systems behind interfaces defined in {@code core-domain}:
 * <ul>
 *   <li>SKAT REST API — VAT return submission and status polling</li>
 *   <li>VIES — EU VAT number validation for intra-community transactions</li>
 *   <li>PEPPOL BIS 3.0 — NemHandel e-invoice transmission</li>
 * </ul>
 *
 * <p>All HTTP calls use Spring {@code WebClient} backed by virtual threads.
 * Sandbox endpoints configured via {@code application.yml} (SKAT_BASE_URL env var).
 * No domain logic lives here — only protocol adaptation and error mapping.
 */
package com.netcompany.vat.skatclient;
