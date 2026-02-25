package com.netcompany.vat.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that all Flyway migrations apply cleanly against a real PostgreSQL container.
 */
class FlywayMigrationIT extends AbstractRepositoryIT {

    @BeforeAll
    static void setUp() {
        initDatabase();
    }

    @Test
    void allMigrationsApplySuccessfully() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        MigrationInfo[] applied = flyway.info().applied();
        assertTrue(applied.length >= 3, "Expected at least 3 migrations, got " + applied.length);

        for (MigrationInfo info : applied) {
            assertNull(info.getState().isFailed() ? "failed" : null,
                    "Migration " + info.getVersion() + " failed");
        }
    }

    @Test
    void allCoreTablesExist() {
        // Verify each table exists by selecting 0 rows without error
        assertDoesNotThrow(() -> ctx.selectOne().from("counterparties").limit(0).execute());
        assertDoesNotThrow(() -> ctx.selectOne().from("tax_periods").limit(0).execute());
        assertDoesNotThrow(() -> ctx.selectOne().from("transactions").limit(0).execute());
        assertDoesNotThrow(() -> ctx.selectOne().from("vat_returns").limit(0).execute());
        assertDoesNotThrow(() -> ctx.selectOne().from("corrections").limit(0).execute());
        assertDoesNotThrow(() -> ctx.selectOne().from("audit_log").limit(0).execute());
    }
}
