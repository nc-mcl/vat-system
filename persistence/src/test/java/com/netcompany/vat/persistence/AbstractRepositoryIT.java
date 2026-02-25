package com.netcompany.vat.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Base class for all persistence integration tests.
 *
 * <p>Starts a single PostgreSQL 16 container per test class, runs all Flyway migrations
 * once via {@code @BeforeAll}, and truncates all mutable tables before each test.
 *
 * <p>The audit_log is intentionally NOT truncated between tests — it is append-only.
 * Tests that need to verify audit events should filter by aggregate_id.
 */
@Testcontainers
public abstract class AbstractRepositoryIT {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("vatdb_test")
                    .withUsername("vatuser")
                    .withPassword("vattest");

    protected static DSLContext ctx;
    protected static ObjectMapper objectMapper;
    protected static DataSource dataSource;

    static {
        // Static initializer runs after POSTGRES is started by Testcontainers JUnit extension.
        // The @Container annotation on a static field ensures the container is started before
        // the first test method.  We rely on the JUnit 5 lifecycle ordering.
    }

    /**
     * Called by subclasses from a {@code @BeforeAll} method to ensure Flyway has run
     * and the shared DSLContext is available.
     */
    protected static void initDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        ctx = DSL.using(dataSource, SQLDialect.POSTGRES);
        objectMapper = new ObjectMapper();
    }

    /** Truncates all mutable tables to ensure test isolation. */
    protected static void truncateTables() {
        // Order matters — FK constraints require child rows deleted before parents
        ctx.deleteFrom(table("corrections")).execute();
        ctx.deleteFrom(table("vat_returns")).execute();
        ctx.deleteFrom(table("transactions")).execute();
        ctx.deleteFrom(table("tax_periods")).execute();
        ctx.deleteFrom(table("counterparties")).execute();
        // audit_log is intentionally NOT cleared — it is append-only
    }
}
