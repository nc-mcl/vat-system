# Persistence Module

## What this is

The `persistence` module implements the data access layer for the VAT system.
It provides:

- **Flyway migrations** — versioned PostgreSQL schema for all core VAT aggregates
- **JOOQ repositories** — type-safe SQL using JOOQ's open-source dynamic DSL
- **Immutable audit log** — append-only event ledger (Bogføringsloven compliance)
- **Domain mappers** — pure-function conversion between JOOQ records and domain objects

This module is a library: it has no `main()` method.  It is consumed by the `api` module,
which provides the Spring Boot application context and DataSource configuration.

## Prerequisites

- Java 21
- Docker (for integration tests — Testcontainers starts PostgreSQL automatically)
- Gradle 8.x (wrapper provided at project root)

For local development with the full stack:

- Docker Compose (starts PostgreSQL 16 via `docker-compose up postgres`)

## How to build

```bash
./gradlew :persistence:compileJava
```

## How to run locally

This module is a library.  To run it as part of the full system:

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run the API (which loads this module and applies Flyway migrations on startup)
./gradlew :api:bootRun
```

Flyway applies all migrations from `src/main/resources/db/migration/` on startup.

## How to run tests

Integration tests use Testcontainers (Docker required):

```bash
./gradlew :persistence:test
```

If Docker is not accessible from the Java process (see note below), tests are
**skipped** (not failed) and the build still succeeds.

To run a single test class:

```bash
./gradlew :persistence:test --tests "com.netcompany.vat.persistence.TaxPeriodRepositoryIT"
```

### Docker Desktop on Windows

Testcontainers requires the Docker daemon to be directly accessible from the Java process.
Docker Desktop on Windows (version 4.25+) uses a proxy architecture that blocks direct
Docker API access from Java.  To enable integration tests locally, choose one of:

1. **Docker Desktop → Settings → General → "Expose daemon on tcp://localhost:2375"** (simplest)
2. **Install Testcontainers Desktop** — https://testcontainers.com/desktop/
3. **Run tests inside the dev container** where `/var/run/docker.sock` is exposed

Integration tests always run in CI (GitHub Actions Linux) where `/var/run/docker.sock` is
available at the standard path.

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/vatdb` | PostgreSQL JDBC URL |
| `DB_USER` | No | `vat` | Database username |
| `DB_PASSWORD` | **Yes in production** | `vat_dev_password` | Database password |

Configured via Spring Boot's `application-persistence.yml`.  In Kubernetes, inject from the
`vat-secrets` Secret (see `infrastructure/k8s/cluster/secrets-template.yaml`).

## Module structure

```
persistence/
  src/main/java/com/netcompany/vat/persistence/
    PersistenceConfiguration.java         ← Spring @Configuration, registers all beans
    repository/
      TaxPeriodRepository.java            ← interface
      TransactionRepository.java          ← interface
      VatReturnRepository.java            ← interface
      CounterpartyRepository.java         ← interface
      CorrectionRepository.java           ← interface
      jooq/
        JooqTaxPeriodRepository.java
        JooqTransactionRepository.java
        JooqVatReturnRepository.java
        JooqCounterpartyRepository.java
        JooqCorrectionRepository.java
    audit/
      AuditEvent.java                     ← record: event to log
      AuditLogger.java                    ← interface
      JooqAuditLogger.java                ← append-only JOOQ implementation
    mapper/
      TaxPeriodMapper.java                ← JOOQ Record → TaxPeriod
      TransactionMapper.java              ← JOOQ Record → Transaction
      VatReturnMapper.java                ← JOOQ Record → VatReturn (JSONB aware)
      CounterpartyMapper.java
      CorrectionMapper.java
  src/main/resources/
    db/migration/
      V001__create_schema.sql             ← counterparties, tax_periods, transactions,
                                             vat_returns, corrections
      V002__create_audit_log.sql          ← audit_log (BIGSERIAL, JSONB payload)
      V003__create_indexes.sql            ← composite indexes for API query patterns
    application-persistence.yml           ← DataSource, Flyway, JOOQ defaults
  src/main/generated/                     ← reserved for future JOOQ code-gen output
  src/test/java/com/netcompany/vat/persistence/
    AbstractRepositoryIT.java             ← shared base: Docker check, Flyway, DSLContext
    FlywayMigrationIT.java                ← all migrations apply cleanly
    TaxPeriodRepositoryIT.java            ← save, find, updateStatus
    TransactionRepositoryIT.java          ← save, findByPeriod, sumVat
    VatReturnRepositoryIT.java            ← full DRAFT→SUBMITTED→ACCEPTED lifecycle
    AuditLoggerIT.java                    ← append-only invariant verified
```

## Database schema overview

### Core tables

| Table | Primary key | Notes |
|---|---|---|
| `counterparties` | UUID | Suppliers and customers |
| `tax_periods` | UUID | Filing periods per jurisdiction |
| `transactions` | UUID | Immutable economic events |
| `vat_returns` | UUID | One return per (period, jurisdiction) |
| `corrections` | UUID | Links original → corrected return |

### Audit log

| Table | Primary key | Notes |
|---|---|---|
| `audit_log` | BIGSERIAL | Append-only.  No DELETE/UPDATE permitted (Bogføringsloven) |

### Key design rules

- All monetary amounts: `BIGINT` in øre (1 DKK = 100 øre) — never DECIMAL/FLOAT
- All enums: `VARCHAR` using Java `.name()` — never ordinal
- `audit_log` rows must never be deleted or updated (5-year retention)
- VAT returns are never deleted — corrections create new returns via `corrections` table

## Spring auto-configuration

The `api` module must scan `com.netcompany.vat.persistence` (or import
`PersistenceConfiguration`) to activate all repository beans.  Example:

```java
@SpringBootApplication(scanBasePackages = "com.netcompany.vat")
public class ApiApplication { ... }
```

The `DSLContext` bean is provided automatically by `spring-boot-starter-jooq` using
`spring.datasource.*` properties.  `ObjectMapper` is provided by Spring Boot's auto-
configuration for Jackson.

## JOOQ code generation (future enhancement)

Current repositories use JOOQ's **dynamic DSL** (`DSL.table()`, `DSL.field()`).  This
compiles without needing a running database.  For type-safe generated classes, add the
`nu.studer.jooq` Gradle plugin (version 9.x) with `DDLDatabase` pointing at
`src/main/resources/db/migration/` and generate into `src/main/generated/`.  The source
set is already registered in `build.gradle.kts`.

## Bogføringsloven compliance

Danish bookkeeping law requires transaction records to be retained for **5 years** and
prohibits retroactive modification.  This is enforced at the application level:

- `AuditLogger` has no `delete` method
- Corrections create new records — original returns are never mutated
- In production, apply a PostgreSQL row-level security policy or a dedicated write-only
  role to `audit_log` to enforce immutability at the database level

## Docker

This module is packaged as part of the `api` service.  See `api/Dockerfile` and the root
`docker-compose.yml`.

## Kubernetes

See `infrastructure/k8s/api/` and `infrastructure/k8s/postgres/` for deployment manifests.
