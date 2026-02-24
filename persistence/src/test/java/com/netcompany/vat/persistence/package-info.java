/**
 * Integration tests for JOOQ repositories.
 * Uses Testcontainers to run tests against a real PostgreSQL instance.
 * Flyway applies the full migration set before each test class.
 */
package com.netcompany.vat.persistence;
