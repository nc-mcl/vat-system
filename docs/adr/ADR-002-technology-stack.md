# ADR-002: Technology Stack

## Status
Accepted

## Context
The VAT system requires a technology stack that can:
1. Support a jurisdiction-agnostic plugin architecture from day one (Phase 1: DK, Phase 3: multi-jurisdiction)
2. Handle financial data with absolute correctness (no floating-point errors)
3. Satisfy strict Danish regulatory requirements (Bogføringsloven immutability, SKAT API integration)
4. Scale to ViDA Digital Reporting Requirements (Phase 2: near-real-time transaction reporting by 2028)
5. Be maintainable by multiple agents working concurrently on different modules

The multi-jurisdiction requirement is the dominant architectural driver. Every technology choice must either actively support it or at minimum not hinder it.

## Decision

### Language & Runtime: Java 21
- **Virtual threads (Project Loom)** provide non-blocking I/O for SKAT and VIES API calls without the complexity of reactive frameworks (WebFlux). `spring.threads.virtual.enabled=true` is a single toggle.
- **Strong typing for financial data**: `long` for monetary amounts (øre, eurocents) is exact and primitive-efficient. Java's type system prevents accidental `double` usage at compile time when architectural conventions are enforced.
- **Sealed interfaces and records** (Java 17+) model immutable domain objects cleanly — `TaxReturn` as a record, `VatResult` as a sealed type with `Payable | Claimable` variants.
- **Pattern matching** (`switch` expressions on sealed types) makes jurisdiction dispatch exhaustive and compiler-checked.
- **Mature enterprise ecosystem**: JOOQ, Flyway, Spring, Testcontainers all have first-class Java 21 support.
- Alternative considered: Kotlin — shares the JVM ecosystem but adds a second language; Java 21 records and sealed types close the expressiveness gap that previously favoured Kotlin.

### Framework: Spring Boot 3.3
- Spring Boot 3.3 targets Jakarta EE 10 and Spring Framework 6.1, both fully Java 21-compatible.
- `@EnableVirtualThreads` (via `spring.threads.virtual.enabled=true`) provides effortless I/O concurrency for SKAT API calls — no `CompletableFuture` chains or reactive types needed.
- Spring MVC with Bean Validation (`@Valid`, `@NotNull`) handles API input validation at the controller boundary; jurisdiction plugins add programmatic validation via `ConstraintValidator`.
- Spring's dependency injection supports the jurisdiction plugin registry: plugins are Spring `@Component` beans collected via `ApplicationContext`, satisfying the zero-core-changes requirement for new jurisdictions.
- Alternative considered: Quarkus — excellent native compilation story but smaller ecosystem for JOOQ and PEPPOL libraries; Spring Boot remains the industry standard for financial/enterprise projects.

### Build: Gradle 8.x with Kotlin DSL (multi-module)
- **Superior to Maven for multi-module projects**: Gradle's incremental build and task-level caching avoid rebuilding unchanged modules. In a six-module project running concurrently across agents, this reduces CI times significantly.
- **Kotlin DSL** (`build.gradle.kts`) provides type-safe autocomplete in IDEs — critical when multiple agents configure build files without running a full compile cycle.
- **Parallel execution** (`--parallel`) allows `core-domain` and `tax-engine` builds to run concurrently, independent of each other.
- **Gradle's `apply false` pattern** lets the root build declare plugin versions centrally without applying Spring Boot to library-only modules (`core-domain`, `tax-engine`).
- Alternative considered: Maven — verbose XML, no incremental task graph, and weaker multi-module build caching. Acceptable for single-module projects; becomes a maintenance burden at six modules.

### Database Access: JOOQ + Flyway
- **JOOQ generates typesafe Java from the database schema at build time**. Every query, JOIN, and INSERT is visible as Java code — no HQL/JPQL string queries, no runtime schema mismatches, no surprise N+1 queries. For a tax system, explicit SQL is both a correctness and an auditability requirement.
- **Immutable event ledger alignment**: The append-only ledger (ADR-001) maps naturally to JOOQ `insertInto(...).values(...)` calls. There are no JOOQ `update()` or `delete()` calls in the ledger path — the absence is structurally enforced by code review, not by framework magic.
- **JSONB for jurisdiction fields**: JOOQ's `JSONB` binding allows storing jurisdiction-specific rubrik fields in a typed PostgreSQL JSONB column. Adding Norway or Germany requires zero schema migrations to core tables — only a new JOOQ binding for the jurisdiction's field shape.
- **Flyway** provides versioned, repeatable migrations with full PostgreSQL DDL support (partial indexes, row-level security, `pg_audit` triggers). PostgreSQL-specific DDL is not abstracted — the schema is written directly for the target database.
- **Row-level security** should be enforced in production (via RLS or a write-only role) to reinforce Bogføringsloven immutability at the database layer. Migrations document the requirement but do not configure RLS by default.
- Alternative considered: Spring Data JPA (Hibernate) — convenient for CRUD entities but actively harmful for an event-sourced, append-only ledger. JPA's session cache and dirty-checking semantics conflict with the immutability requirement. JOOQ's explicit SQL model is the right abstraction for financial systems.

### Validation: Jakarta Bean Validation 3.0
- **`@Valid` + `@NotNull` / `@NotBlank`** on API DTOs provide declarative boundary validation at the Spring MVC controller layer, with automatic `400 Bad Request` responses for malformed input.
- **Custom `ConstraintValidator<A, T>`** implementations allow jurisdiction plugins to contribute validation logic as Spring beans — a `DkFilingValidator` implements the SKAT rubrik cross-field rules without any changes to the core validation framework.
- **Separation of concerns**: Bean Validation handles structural/format validation (field presence, string patterns, numeric ranges). Domain validation (VAT arithmetic consistency, period boundary checks) lives in `/tax-engine` as plain Java with no Jakarta dependencies.
- Alternative considered: custom validation framework — unnecessary given Jakarta Validation's maturity and Spring Boot's native integration.

### Testing: JUnit 5 + Mockito + Testcontainers
- **JUnit 5** parametrized tests (`@ParameterizedTest`, `@MethodSource`) are essential for multi-jurisdiction VAT scenarios: the same test logic runs against different jurisdiction plugins, rate tables, and filing calendars.
- **Mockito** isolates jurisdiction plugin components in unit tests — a `DkFilingScheduleProvider` test does not require a live database or SKAT sandbox.
- **Testcontainers** runs integration tests against a real PostgreSQL container, not an H2 in-memory substitute. H2 does not support PostgreSQL-specific DDL (row-level security, `pg_audit`, partial indexes) used by the immutable ledger. Testcontainers tests reflect production behaviour exactly.
- **Test slices** (`@DataJooqTest`, `@WebMvcTest`) allow fast, focused tests for JOOQ repositories and Spring MVC controllers independently.
- Alternative considered: H2 in-memory database — rejects PostgreSQL-specific DDL, cannot test RLS, and creates a false sense of test coverage for a compliance system. Rejected.

### Monetary Arithmetic: `long` (smallest currency unit)
- All monetary values stored and computed as `long` representing øre (DKK), eurocents, or the jurisdiction's smallest unit.
- Eliminates IEEE 754 floating-point errors — a compliance requirement for tax systems.
- `long` arithmetic is exact for the operations used in VAT: addition, subtraction, and percentage calculation via basis points (`vatAmount = taxableAmount * basisPoints / 10_000`).
- VAT rates expressed as basis points: `2500` = 25.00%. The `/ 10_000` division is integer division with no floating-point rounding.
- Enforced by a value object: `record MonetaryAmount(long oere)`. No raw `long` passes across module boundaries without a `MonetaryAmount` wrapper.

### Module Structure
```
/core-domain          ← Java interfaces, records, enums, JurisdictionPlugin SPI; no framework deps
/tax-engine           ← Rate calculation, classification, reverse charge (pure Java, no infra)
/persistence          ← JOOQ DSL, Flyway migrations, repository implementations
/api                  ← Spring Boot application, REST controllers, Bean Validation DTOs
/skat-client          ← SKAT + VIES REST clients, PEPPOL adapters (Spring WebClient)
/mcp-server           ← Claude Code MCP tool (TypeScript/Node.js — separate from Java backend)
```

## Consequences

**Positive:**
- JOOQ's compile-time query checking eliminates an entire class of runtime SQL errors in production
- Virtual threads make SKAT API I/O non-blocking with zero reactive boilerplate — code reads as sequential Java
- Testcontainers ensures the test PostgreSQL schema matches production exactly, including RLS and audit triggers
- Gradle's incremental build and parallel execution make multi-agent concurrent development practical
- Sealed `JurisdictionPlugin` interface + Spring `@Component` collection means adding Norway = one new module, zero core changes
- `long` basis-point arithmetic is exact, auditable, and trivially unit-testable

**Negative:**
- JOOQ requires a running PostgreSQL to generate the schema-bound DSL classes at build time — a `docker-compose up db` step is needed before the first build
- Testcontainers adds Docker as a CI dependency — acceptable for a compliance system where test fidelity is paramount
- Java's verbosity relative to TypeScript/Kotlin increases boilerplate for simple DTOs — mitigated by Java 21 records and Lombok where appropriate

## Alternatives Considered

| Option | Reason Rejected |
|---|---|
| Spring Data JPA (Hibernate) | ORM magic, session cache, and dirty-checking conflict with append-only immutable ledger; JPQL hides SQL from compliance reviewers |
| Maven | Verbose XML configuration; no incremental build caching; inferior multi-module developer experience |
| Kotlin | Adds a second JVM language; Java 21 records and sealed types close the expressiveness gap; mixed-language modules complicate agent coordination |
| H2 in-memory DB for tests | Does not support PostgreSQL-specific DDL (RLS, `pg_audit`); creates false test coverage for a compliance system |
| Reactive (WebFlux + R2DBC) | Reactive programming model adds complexity with no benefit given Java 21 virtual threads; JOOQ has limited R2DBC support |
| Python + FastAPI | No compile-time type safety for financial types; ecosystem maturity for PEPPOL and SKAT integration lags Java |
