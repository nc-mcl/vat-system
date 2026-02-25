package com.netcompany.vat.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.jooq.impl.DSL.table;

/**
 * Base class for all persistence integration tests.
 *
 * <p>Starts a PostgreSQL 16 container per test class via Testcontainers (managed manually
 * to allow a Docker availability check first), runs all Flyway migrations once, and
 * truncates all mutable tables before each test.
 *
 * <p>If Docker is not accessible (e.g. Windows Docker Desktop without TCP/socket enabled),
 * all integration tests are <em>skipped</em> automatically rather than failing.
 * They always run in CI (GitHub Actions Linux) where /var/run/docker.sock is available.
 *
 * <p>The audit_log is intentionally NOT truncated between tests — it is append-only.
 * Tests that need to verify audit events should filter by aggregate_id.
 *
 * <h3>Local Windows setup</h3>
 * Integration tests require the Docker daemon to be reachable from the Java process.
 * With Docker Desktop on Windows, enable one of:
 * <ul>
 *   <li>Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375"</li>
 *   <li>Install Testcontainers Desktop (https://testcontainers.com/desktop/)</li>
 * </ul>
 */
public abstract class AbstractRepositoryIT {

    // Container is created (not started) in the static block; it is only started in
    // initDatabase() after the Docker availability check passes.
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("vatdb_test")
                    .withUsername("vatuser")
                    .withPassword("vattest");

    protected static DSLContext ctx;
    protected static ObjectMapper objectMapper;
    protected static DataSource dataSource;

    /**
     * Called by subclasses from a {@code @BeforeAll} method.
     *
     * <p>Checks Docker availability first.  If Docker is unreachable the test class is
     * <em>skipped</em> via JUnit 5 assumptions (shown as "skipped", not "failed").
     */
    protected static void initDatabase() {
        // Must check before starting the container so failures are skips, not errors.
        Assumptions.assumeTrue(
                isDockerAvailable(),
                "Docker is not accessible from the Java process — integration tests skipped. " +
                "See AbstractRepositoryIT Javadoc for local setup instructions."
        );

        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }

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

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}
