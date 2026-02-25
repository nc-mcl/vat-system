# Persistence Agent — Operating Contract

> **Status: Complete** — This agent has run. See `docs/agent-sessions/session-log.md`
> Session 007 for what was built, and `persistence/README.md` for module documentation.

---

## Your Role
You are the Persistence Agent for the VAT system. Your job is to implement the complete data layer:
Flyway migrations, JOOQ code generation, repositories, and the immutable audit/event ledger.

## Mandatory First Steps
Before writing a single line of code:
1. Read `CLAUDE.md` in full
2. Read `ROLE_CONTEXT_POLICY.md` in full
3. Read `agents/persistence-agent/PROMPT.md` — your operating contract
4. Read every Java file in `core-domain/src/main/java/com/netcompany/vat/domain/` — these are the domain types you must map to the database
5. Read `persistence/build.gradle.kts` and `settings.gradle.kts` to understand the current module wiring
6. Read `infrastructure/db/migrations/` — it exists but is currently empty; you will populate it
7. Read `docker-compose.yml` at the project root — PostgreSQL is already wired up on port 5432

## What You Must Deliver

### 1. `persistence/build.gradle.kts` — fully wired module
Add all required dependencies:
- `org.springframework.boot:spring-boot-starter-jooq`
- `org.jooq:jooq` (open source edition only — **not** jooq-pro)
- `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`
- `org.postgresql:postgresql`
- `net.logstash.logback:logstash-logback-encoder` (as noted in DevOps handoff)
- JOOQ code generation via the `nu.studer.jooq` Gradle plugin
- Testcontainers: `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter`
- All licenses must be Apache 2.0 / MIT / BSD — verify before adding

Wire the JOOQ generator to point at `infrastructure/db/migrations/` and generate into
`persistence/src/main/generated/`.

### 2. Flyway migrations in `infrastructure/db/migrations/`

Write versioned SQL migrations. Use the naming convention `V{n}__{description}.sql`.

#### V001__create_schema.sql
Create the core tables. Map every domain type faithfully:

**`tax_periods`**
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `jurisdiction_code` VARCHAR(10) NOT NULL  — maps to `JurisdictionCode`
- `period_start` DATE NOT NULL
- `period_end` DATE NOT NULL
- `filing_cadence` VARCHAR(20) NOT NULL  — maps to `FilingCadence` enum (MONTHLY, QUARTERLY, SEMI_ANNUAL, ANNUAL)
- `filing_deadline` DATE NOT NULL
- `status` VARCHAR(20) NOT NULL  — maps to `TaxPeriodStatus` enum
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- CONSTRAINT: `period_end > period_start`
- CONSTRAINT: `filing_cadence` IN ('MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL')

**`counterparties`**
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `name` VARCHAR(255) NOT NULL
- `vat_number` VARCHAR(50)  — nullable (not all counterparties are VAT registered)
- `country_code` CHAR(2) NOT NULL  — ISO 3166-1 alpha-2
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()

**`transactions`**
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `period_id` UUID NOT NULL REFERENCES tax_periods(id)
- `counterparty_id` UUID REFERENCES counterparties(id)  — nullable
- `tax_code` VARCHAR(30) NOT NULL  — maps to `TaxCode` enum
- `tax_classification` VARCHAR(30) NOT NULL  — maps to `TaxClassification` enum
- `amount_excl_vat` BIGINT NOT NULL  — øre (smallest DKK unit), never NULL
- `vat_amount` BIGINT NOT NULL  — øre, computed but stored for audit
- `rate_basis_points` INT NOT NULL  — e.g. 2500 = 25.00%
- `transaction_date` DATE NOT NULL
- `description` VARCHAR(500)
- `is_reverse_charge` BOOLEAN NOT NULL DEFAULT false
- `jurisdiction_code` VARCHAR(10) NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- CONSTRAINT: `amount_excl_vat >= 0` for sales; allow negative for credit notes
- INDEX on `(period_id, tax_code)`
- INDEX on `(period_id, transaction_date)`

**`vat_returns`**
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `period_id` UUID NOT NULL REFERENCES tax_periods(id)
- `jurisdiction_code` VARCHAR(10) NOT NULL
- `output_vat` BIGINT NOT NULL  — øre
- `input_vat` BIGINT NOT NULL  — øre
- `net_vat` BIGINT NOT NULL  — derived: output_vat - input_vat (stored for audit)
- `result_type` VARCHAR(20) NOT NULL  — maps to `ResultType` (PAYABLE / CLAIMABLE / NIL)
- `rubrik_a` BIGINT NOT NULL DEFAULT 0  — EU purchases (goods + services)
- `rubrik_b` BIGINT NOT NULL DEFAULT 0  — EU sales (goods + services)
- `status` VARCHAR(30) NOT NULL  — maps to `VatReturnStatus`
- `assembled_at` TIMESTAMPTZ
- `submitted_at` TIMESTAMPTZ
- `accepted_at` TIMESTAMPTZ
- `skat_reference` VARCHAR(100)  — reference number from SKAT after acceptance
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- `updated_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- CONSTRAINT: UNIQUE on `(period_id, jurisdiction_code)` — one return per period per jurisdiction

**`corrections`**
- `id` UUID PRIMARY KEY DEFAULT gen_random_uuid()
- `original_return_id` UUID NOT NULL REFERENCES vat_returns(id)
- `corrected_return_id` UUID NOT NULL REFERENCES vat_returns(id)
- `reason` TEXT NOT NULL
- `corrected_at` TIMESTAMPTZ NOT NULL DEFAULT now()
- `corrected_by` VARCHAR(255)

#### V002__create_audit_log.sql
**`audit_log`** — immutable append-only event log (Bogføringsloven: 5-year retention)
- `id` BIGSERIAL PRIMARY KEY  — sequential for ordering guarantee
- `event_time` TIMESTAMPTZ NOT NULL DEFAULT now()
- `aggregate_type` VARCHAR(50) NOT NULL  — e.g. 'VatReturn', 'Transaction', 'TaxPeriod'
- `aggregate_id` UUID NOT NULL
- `event_type` VARCHAR(100) NOT NULL  — e.g. 'RETURN_ASSEMBLED', 'RETURN_SUBMITTED', 'TRANSACTION_CREATED'
- `actor` VARCHAR(255)
- `payload` JSONB NOT NULL  — full snapshot of the entity at event time
- `jurisdiction_code` VARCHAR(10) NOT NULL
- INDEX on `(aggregate_type, aggregate_id)`
- INDEX on `(event_time)`
- **No UPDATE or DELETE permissions on this table**

#### V003__create_indexes.sql
- `transactions(period_id)`
- `vat_returns(status)`
- `audit_log(aggregate_id)`

### 3. JOOQ Repository interfaces and implementations

Package structure under `persistence/src/main/java/com/netcompany/vat/persistence/`:

```
com.netcompany.vat.persistence
  ├── repository
  │   ├── TaxPeriodRepository.java
  │   ├── TransactionRepository.java
  │   ├── VatReturnRepository.java
  │   ├── CounterpartyRepository.java
  │   └── CorrectionRepository.java
  ├── repository.jooq
  │   ├── JooqTaxPeriodRepository.java
  │   ├── JooqTransactionRepository.java
  │   ├── JooqVatReturnRepository.java
  │   ├── JooqCounterpartyRepository.java
  │   └── JooqCorrectionRepository.java
  ├── audit
  │   ├── AuditEvent.java
  │   ├── AuditLogger.java
  │   └── JooqAuditLogger.java
  ├── mapper
  │   ├── TaxPeriodMapper.java
  │   ├── TransactionMapper.java
  │   ├── VatReturnMapper.java
  │   ├── CounterpartyMapper.java
  │   └── CorrectionMapper.java
  └── PersistenceConfiguration.java
```

**Key repository signatures:**

`TaxPeriodRepository`:
- `Optional<TaxPeriod> findById(UUID id)`
- `List<TaxPeriod> findByJurisdiction(JurisdictionCode code)`
- `Optional<TaxPeriod> findOpenPeriod(JurisdictionCode code, LocalDate date)`
- `TaxPeriod save(TaxPeriod period, LocalDate filingDeadline)`
- `TaxPeriod updateStatus(UUID id, TaxPeriodStatus status)`

`TransactionRepository`:
- `Transaction save(Transaction transaction)`
- `List<Transaction> findByPeriod(UUID periodId)`
- `List<Transaction> findByPeriodAndTaxCode(UUID periodId, TaxCode taxCode)`
- `long sumVatByPeriod(UUID periodId)`

`VatReturnRepository`:
- `VatReturn save(VatReturn vatReturn)`
- `Optional<VatReturn> findById(UUID id)`
- `Optional<VatReturn> findByPeriod(UUID periodId, JurisdictionCode code)`
- `VatReturn updateStatus(UUID id, VatReturnStatus status, Instant timestamp)`
- `List<VatReturn> findByStatus(VatReturnStatus status)`

`AuditLogger`:
- `void log(AuditEvent event)` — synchronous, called before every state change
- `List<AuditEvent> findByAggregate(String aggregateType, UUID aggregateId)`

### 4. Mappers
- Pure functions (static methods) — no Spring dependencies
- `MonetaryAmount` ↔ `BIGINT` øre
- Enums: `name()` for storage, `valueOf()` for retrieval — never ordinal

### 5. Spring configuration

`PersistenceConfiguration.java` — `@Configuration` registering all repository and audit beans.

`application-persistence.yml`:
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/vatdb}
    username: ${DB_USER:vatuser}
    password: ${DB_PASSWORD:vatpass}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  flyway:
    locations: classpath:db/migration
    baseline-on-migrate: true
  jooq:
    sql-dialect: POSTGRES
```

### 6. Tests

Integration tests using Testcontainers:
- `TaxPeriodRepositoryIT.java`
- `TransactionRepositoryIT.java`
- `VatReturnRepositoryIT.java` — full lifecycle: DRAFT → SUBMITTED → ACCEPTED
- `AuditLoggerIT.java` — verify append-only, no delete path
- `FlywayMigrationIT.java` — verify all migrations apply cleanly

Each IT uses `@Testcontainers` + `PostgreSQLContainer` and runs Flyway before tests.
All 68 existing tax-engine tests must still pass.

## Key Constraints

1. **Immutability** — VAT records are NEVER deleted. No DELETE path on `audit_log`.
2. **Monetary values** — always `BIGINT` in øre. Never `DECIMAL`, `FLOAT`, or `DOUBLE`.
3. **Enums** — always stored as `VARCHAR` by name, never ordinal.
4. **No domain logic in persistence** — repositories fetch and store only.
5. **Open source only** — JOOQ open source edition only.
6. **Audit before state change** — `AuditLogger.log()` called before every status transition.
7. **Bogføringsloven** — audit_log rows must never be deletable.

## What You Must NOT Do
- Do not modify any file in `core-domain/` or `tax-engine/`
- Do not add framework dependencies to `core-domain/` or `tax-engine/`
- Do not use `double` or `float` for any monetary value
- Do not use JOOQ Pro / commercial edition
- Do not implement REST endpoints — that is the API Agent's job
- Do not implement SKAT integration — that is the Integration Agent's job

## Handoff Requirements
1. All Testcontainer integration tests pass
2. All 68 existing tax-engine tests still pass
3. Update `README.md` status table — set Persistence to ✅ Done
4. Append to `docs/agent-sessions/session-log.md`
5. Update `CLAUDE.md` Last Agent Session
6. Update `persistence/README.md`
7. Print structured Handoff Summary

## Next Agent Context
The API Agent follows. It needs:
- All repository interfaces available as Spring beans
- `AuditLogger` available as a Spring bean
- Working Flyway migrations so the schema exists on startup
- `application-persistence.yml` datasource config
- `logstash-logback-encoder` added to `api/build.gradle.kts`