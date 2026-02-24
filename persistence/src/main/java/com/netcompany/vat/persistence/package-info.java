/**
 * Persistence module — JOOQ data access layer and Flyway schema migrations.
 *
 * <p>Provides:
 * <ul>
 *   <li>JOOQ-based repository implementations for all core aggregates</li>
 *   <li>Append-only write path for the immutable audit event ledger</li>
 *   <li>Flyway versioned migrations under {@code db/migration/}</li>
 *   <li>PostgreSQL row-level security enforcement for Bogføringsloven compliance</li>
 * </ul>
 *
 * <p>JOOQ DSL classes ({@code Tables}, {@code Records}) are generated from the live
 * PostgreSQL schema at build time via the {@code jooqGenerate} Gradle task.
 * Run {@code docker-compose up db} before the first build.
 */
package com.netcompany.vat.persistence;
