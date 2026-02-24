/**
 * Core domain module — jurisdiction-agnostic types, interfaces, and value objects.
 *
 * <p>This package contains the fundamental building blocks of the multi-jurisdiction VAT system:
 * <ul>
 *   <li>{@code JurisdictionPlugin} — SPI interface every jurisdiction must implement</li>
 *   <li>{@code Money} — value object for exact monetary arithmetic (smallest currency unit)</li>
 *   <li>{@code TaxReturn}, {@code Invoice}, {@code Transaction} — core aggregates</li>
 *   <li>{@code AuditEvent} — immutable audit log entry (Bogføringsloven compliance)</li>
 * </ul>
 *
 * <p><strong>Dependency rule:</strong> this module has zero dependencies on Spring, JOOQ,
 * or any other infrastructure framework. It must remain compilable as plain Java 21.
 */
package com.netcompany.vat.domain;
