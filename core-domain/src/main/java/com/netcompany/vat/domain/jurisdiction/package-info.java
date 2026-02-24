/**
 * Jurisdiction Plugin SPI (Service Provider Interface).
 *
 * <p>This package defines the {@link com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin}
 * interface that every country implementation must satisfy. The interface is the only contract
 * the core system depends on — concrete implementations live in jurisdiction sub-packages
 * (e.g. {@code com.netcompany.vat.domain.dk}).
 *
 * <p>Adding a new jurisdiction requires:
 * <ol>
 *   <li>Creating a new sub-package (e.g. {@code com.netcompany.vat.domain.no})</li>
 *   <li>Implementing {@code JurisdictionPlugin} for that jurisdiction</li>
 *   <li>Adding the jurisdiction code to {@link com.netcompany.vat.domain.JurisdictionCode}</li>
 *   <li>Registering the plugin in the API module at startup</li>
 * </ol>
 *
 * <p>Zero changes to core domain logic, tax-engine, or persistence are required.
 */
package com.netcompany.vat.domain.jurisdiction;
