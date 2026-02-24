/**
 * Danish VAT jurisdiction plugin (MOMS, administered by SKAT).
 *
 * <p>This package provides the concrete implementation of
 * {@link com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin} for Denmark.
 * It is the reference implementation and the first jurisdiction supported by the system.
 *
 * <p>Danish-specific rules encoded here:
 * <ul>
 *   <li>Standard rate: 25% (2500 basis points)</li>
 *   <li>Zero-rated: exports, intra-EU goods supplies</li>
 *   <li>Exempt: healthcare, education, insurance (no VAT recovery)</li>
 *   <li>Reverse charge: B2B cross-border services (buyer self-assesses)</li>
 *   <li>Monthly filing threshold: annual turnover &gt; 50M DKK</li>
 *   <li>Quarterly filing: annual turnover 5M–50M DKK (default)</li>
 *   <li>Semi-annual filing: annual turnover &lt; 5M DKK</li>
 *   <li>Filing deadline: 10th of the month following the period</li>
 *   <li>Authority: SKAT (Skattestyrelsen)</li>
 * </ul>
 *
 * <p>ViDA obligations: not yet active. {@code isVidaEnabled()} returns {@code false} until Phase 2.
 */
package com.netcompany.vat.domain.dk;
