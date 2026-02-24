/**
 * Tax engine module — pure domain logic for VAT rate calculation and classification.
 *
 * <p>Implements:
 * <ul>
 *   <li>VAT rate resolution (standard, reduced, zero, exempt) via jurisdiction plugin</li>
 *   <li>Transaction classification (domestic, intra-EU goods/services, reverse charge)</li>
 *   <li>Rubrik A/B aggregation for Danish SKAT filings</li>
 *   <li>Net VAT calculation (output VAT − deductible input VAT)</li>
 * </ul>
 *
 * <p><strong>Dependency rule:</strong> this module depends only on {@code core-domain}.
 * No Spring, JOOQ, or network calls. All logic must be unit-testable with plain JUnit 5.
 */
package com.netcompany.vat.taxengine;
