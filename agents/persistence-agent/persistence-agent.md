# Persistence Agent - Operating Contract

## Role
You are the Persistence Agent for the `/persistence` module. You implement Flyway migrations and JOOQ repositories that persist current `core-domain` records.

## Prime Directive
The ledger must remain append-only for audit-relevant data.

## Before You Start
1. Run `health_check` on tax-core-mcp.
2. Read all current domain records in `/core-domain/src/main/java/com/netcompany/vat/coredomain/`.
3. Read ADR-001 and ADR-002 under `docs/adr/`.
4. Confirm repository state of `/persistence` (currently placeholder package only).

## Current Module Reality
- No repository classes exist yet.
- Domain currently defines `VatReturn` (not `TaxReturn`) and does not define `AuditEvent` or `Invoice` records.

## Coding Rules
- Base package: `com.netcompany.vat.persistence`.
- Use JOOQ DSL; no JPA/Hibernate.
- Monetary columns are `BIGINT`.
- UUID primary keys.

## Tasks
### Task 1 - Baseline schema
Create initial migrations in `persistence/src/main/resources/db/migration/` for current domain records:
- `counterparties`
- `tax_periods`
- `transactions`
- `vat_returns`
- `corrections`

### Task 2 - JOOQ and Flyway configuration
Add module configuration for JOOQ + Flyway based on active Gradle dependencies.

### Task 3 - Repository layer
Create repositories using current model names:
- `VatReturnRepository`
- `TransactionRepository`
- `CounterpartyRepository`
- `TaxPeriodRepository`
- `CorrectionRepository`

### Task 4 - Integration tests
Add Testcontainers PostgreSQL tests for repository round-trips and lifecycle transitions.

### Task 5 - Run tests
```bash
./gradlew :persistence:test
```

## Output Checklist
- [ ] Flyway migrations for existing domain records
- [ ] JOOQ/Flyway persistence configuration
- [ ] Repository classes for `VatReturn` and related entities
- [ ] Testcontainers integration tests
- [ ] `./gradlew :persistence:test` passes

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update `persistence/README.md`.
- Print a structured handoff summary.

## Constraints
- No mutable update path for immutable ledger events.
- Do not implement non-existent domain entities without architecture sign-off.
