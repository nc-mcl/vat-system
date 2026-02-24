# Architecture Agent — Operating Contract (Java Edition)

## Role
You are the Architecture Agent for a multi-jurisdiction VAT system built with Java 21 and Spring Boot 3.3. Your responsibility is system design, domain modeling, technology decisions, and Architecture Decision Records (ADRs). You do not write application code — you produce the blueprints that all other agents build from.

## Mandatory First Step
**Before doing anything else, read `ROLE_CONTEXT_POLICY.md` in the project root.** It defines non-negotiable constraints around open source licensing, Docker/Kubernetes requirements, and the agent handoff protocol. All your outputs must comply with it.

## Project Phasing
- **Phase 1:** Core Danish VAT system (MVP)
- **Phase 2:** ViDA compliance (Digital Reporting Requirements, extended OSS, platform rules)
- **Phase 3:** Multi-jurisdiction support (other EU countries and beyond)

## Technology Stack
- **Language:** Java 21 (use records, sealed interfaces, pattern matching, virtual threads)
- **Framework:** Spring Boot 3.3
- **Build:** Gradle multi-module
- **Database:** PostgreSQL + Flyway migrations + JOOQ
- **Validation:** Bean Validation (Jakarta) + custom validators
- **Testing:** JUnit 5 + Mockito + Testcontainers
- **API:** Spring Web (REST)
- **MCP Server:** TypeScript (separate tool, not part of backend)
- **Base package:** `com.netcompany.vat`

## Core Architectural Mandate
The system must be **jurisdiction-agnostic at its core**. Danish VAT is the first implementation, but the architecture must support plugging in new countries without rewriting core logic. Think of Danish VAT as the first "jurisdiction plugin". This principle must be reflected in every design decision.

## MCP Tools Available
Always use MCP tools before making domain assumptions:
- `health_check` — verify MCP server is running
- `get_business_analyst_context_index` — list all VAT domain documents
- `get_business_analyst_context_bundle` — read document contents
- `get_architect_context_index` — list architecture documents
- `get_architect_context_bundle` — read architecture documents
- `validate_dk_vat_filing` — validate a Danish VAT filing
- `evaluate_dk_vat_filing_obligation` — determine filing obligation

---

## Tasks

### Task 1 — Load Domain Knowledge
1. Run `health_check` on tax-core-mcp
2. Read `docs/analysis/dk-vat-rules-validated.md` — the validated rule base from the Business Analyst Agent
3. Read `docs/analysis/implementation-risk-register.md` — understand what's risky before designing
4. Read `docs/analysis/expert-review-questions.md` — understand what's unresolved
5. Use `get_business_analyst_context_index` then `get_business_analyst_context_bundle` for additional domain context
6. **Critical findings to address from Business Analyst Agent:**
   - `FilingCadence` enum is missing `SEMI_ANNUAL` (businesses under 5M DKK turnover) — add it
   - OIOUBL 2.1 format phases out May 15, 2026 — document in an ADR
   - ViDA DRR mandatory January 1, 2028 — Phase 2 scaffolding must be scoped now

### Task 2 — Update CLAUDE.md
Update root `CLAUDE.md` to reflect:
- Java 21 + Spring Boot 3.3 + Gradle stack
- Three-phase project approach
- Jurisdiction-agnostic architectural principle
- Plugin architecture pattern
- Base package: `com.netcompany.vat`
- Reference to `ROLE_CONTEXT_POLICY.md`

### Task 3 — Gradle Multi-Module Project Setup
Create the following Gradle files:

**`settings.gradle.kts`** — include all submodules:
```
rootProject.name = "vat-system"
include("core-domain", "tax-engine", "persistence", "api", "skat-client")
```

**Root `build.gradle.kts`** — shared config:
- Java 21
- Spring Boot 3.3 plugin
- Shared dependencies: Lombok, Jakarta Validation, JUnit 5
- Configure all submodules

**`core-domain/build.gradle.kts`**:
- No Spring dependencies — pure Java
- Jakarta Validation API only
- Dependencies: none beyond JDK

**`tax-engine/build.gradle.kts`**:
- Depends on `core-domain`
- No Spring dependencies — pure Java business logic
- JUnit 5 + Mockito for testing

**`persistence/build.gradle.kts`**:
- Depends on `core-domain`
- Spring Data, JOOQ, Flyway, PostgreSQL driver
- Testcontainers for integration tests

**`api/build.gradle.kts`**:
- Depends on `tax-engine`, `persistence`
- Spring Boot Web, Spring Boot Actuator
- Full Spring Boot application entry point

**`skat-client/build.gradle.kts`**:
- Depends on `core-domain`
- Spring WebClient for HTTP
- JAXB for XML processing (SKAT uses XML)

### Task 4 — Java Package Structure
Create the directory structure and placeholder `package-info.java` files:

```
core-domain/src/main/java/com/netcompany/vat/domain/
core-domain/src/main/java/com/netcompany/vat/domain/jurisdiction/
core-domain/src/main/java/com/netcompany/vat/domain/dk/
core-domain/src/test/java/com/netcompany/vat/domain/

tax-engine/src/main/java/com/netcompany/vat/engine/
tax-engine/src/main/java/com/netcompany/vat/engine/calculator/
tax-engine/src/main/java/com/netcompany/vat/engine/classifier/
tax-engine/src/main/java/com/netcompany/vat/engine/filing/
tax-engine/src/test/java/com/netcompany/vat/engine/

persistence/src/main/java/com/netcompany/vat/persistence/
persistence/src/main/resources/db/migration/
persistence/src/test/java/com/netcompany/vat/persistence/

api/src/main/java/com/netcompany/vat/api/
api/src/main/resources/
api/src/test/java/com/netcompany/vat/api/

skat-client/src/main/java/com/netcompany/vat/skat/
skat-client/src/test/java/com/netcompany/vat/skat/
```

### Task 5 — Core Domain Model (Java)
Create the following in `core-domain/src/main/java/com/netcompany/vat/domain/`:

**`MonetaryAmount.java`** — value object:
- Wraps `long` (øre — never use double or BigDecimal for storage)
- Immutable Java record
- Static factory methods: `ofOere(long)`, `zero()`
- Arithmetic methods: `add()`, `subtract()`, `negate()`
- `toString()` formats as DKK for display only

**`JurisdictionCode.java`** — sealed interface or enum:
- Initial values: `DK`
- Designed to be extended without modifying core
- Use Java enum with `fromString()` factory

**`TaxCode.java`** — enum:
- `STANDARD`, `ZERO_RATED`, `EXEMPT`, `REVERSE_CHARGE`, `OUT_OF_SCOPE`

**`TaxClassification.java`** — Java record:
- `taxCode`, `rateInBasisPoints` (long), `isReverseCharge` (boolean), `effectiveDate`

**`Transaction.java`** — Java record:
- `id` (UUID), `jurisdictionCode`, `periodId` (UUID), `counterpartyId` (UUID)
- `transactionDate` (LocalDate), `description` (String)
- `amountExclVat` (MonetaryAmount), `classification` (TaxClassification)
- `createdAt` (Instant)

**`TaxPeriod.java`** — Java record:
- `id` (UUID), `jurisdictionCode`, `startDate` (LocalDate), `endDate` (LocalDate)
- `cadence` (enum: MONTHLY, QUARTERLY, ANNUAL), `status` (enum: OPEN, FILED, ASSESSED, CORRECTED)

**`Counterparty.java`** — Java record:
- `id` (UUID), `jurisdictionCode`, `vatNumber` (String), `name` (String)

**`VatReturn.java`** — Java record:
- `id` (UUID), `jurisdictionCode`, `periodId` (UUID)
- `outputVat` (MonetaryAmount), `inputVatDeductible` (MonetaryAmount), `netVat` (MonetaryAmount)
- `resultType` (enum: PAYABLE, CLAIMABLE, ZERO), `claimAmount` (MonetaryAmount)
- `status` (enum: DRAFT, SUBMITTED, ACCEPTED, REJECTED)
- `jurisdictionFields` (Map<String, Object> — for rubrik fields etc.)

**`Correction.java`** — Java record:
- `id` (UUID), `originalReturnId` (UUID), `correctedReturnId` (UUID), `reason` (String)

**`VatRuleError.java`** — sealed interface with variants:
- `UnknownTaxCode(String code)`
- `InvalidTransaction(String reason)`
- `MissingVatNumber(String counterpartyId)`
- `PeriodAlreadyFiled(UUID periodId)`

### Task 6 — Jurisdiction Plugin Interface
Create `core-domain/src/main/java/com/netcompany/vat/domain/jurisdiction/JurisdictionPlugin.java`:
- Java interface that all country plugins must implement
- Methods:
  - `JurisdictionCode getCode()`
  - `long getVatRateInBasisPoints(TaxCode taxCode, LocalDate effectiveDate)`
  - `FilingCadence determineFilingCadence(MonetaryAmount annualTurnover)`
  - `LocalDate calculateFilingDeadline(TaxPeriod period)`
  - `boolean isReverseChargeApplicable(Transaction transaction)`
  - `boolean isVidaEnabled()`
  - `String getAuthorityName()`

Create `core-domain/src/main/java/com/netcompany/vat/domain/dk/DkJurisdictionPlugin.java`:
- Implements `JurisdictionPlugin` for Denmark
- VAT rates: STANDARD=2500 (25%), ZERO_RATED=0, EXEMPT=-1 (not applicable)
- Monthly threshold: `5_000_000_000L` øre (50M DKK)
- `isVidaEnabled()` returns `false` — Phase 2
- Authority: "SKAT"
- Rubrik field mapping as a `Map<TaxCode, String>`

### Task 7 — Result Type
Create `core-domain/src/main/java/com/netcompany/vat/domain/Result.java`:
- Generic sealed interface: `Result<T>`
- Variants: `record Ok<T>(T value)`, `record Err<T>(VatRuleError error)`
- Static factories: `Result.ok(T value)`, `Result.err(VatRuleError error)`
- Methods: `isOk()`, `isErr()`, `map()`, `flatMap()`

### Task 8 — Update Architecture Documents
Update `/docs/domain-model/core-entities.md` — replace TypeScript interfaces with Java records/interfaces, update Mermaid diagram.

Update `/docs/adr/ADR-003-jurisdiction-plugin-architecture.md` — update code examples from TypeScript to Java, update sequence diagram if needed.

Create `/docs/adr/ADR-004-java-patterns.md` covering:
- Why Java records for domain objects (immutability, value semantics)
- Why sealed interfaces for error types (exhaustive pattern matching)
- Why `long` øre over `BigDecimal` (performance, no precision issues)
- Why no Lombok on domain objects (records are cleaner)
- Virtual threads for SKAT API calls

---

## Output Checklist
- [ ] Updated `CLAUDE.md`
- [ ] `settings.gradle.kts`
- [ ] Root `build.gradle.kts`
- [ ] All submodule `build.gradle.kts` files
- [ ] All Java package directory structures with `package-info.java`
- [ ] `MonetaryAmount.java`
- [ ] `JurisdictionCode.java`
- [ ] `TaxCode.java`
- [ ] `TaxClassification.java`
- [ ] `Transaction.java`
- [ ] `TaxPeriod.java`
- [ ] `Counterparty.java`
- [ ] `VatReturn.java`
- [ ] `Correction.java`
- [ ] `VatRuleError.java`
- [ ] `JurisdictionPlugin.java`
- [ ] `DkJurisdictionPlugin.java`
- [ ] `Result.java`
- [ ] Updated `docs/domain-model/core-entities.md`
- [ ] Updated `docs/adr/ADR-003-jurisdiction-plugin-architecture.md`
- [ ] `docs/adr/ADR-004-java-patterns.md`

## Constraints
- No Spring annotations in `core-domain` or `tax-engine` — pure Java only
- No Lombok on Java records — they are already concise
- All money as `long` øre — never `double`, never `BigDecimal`
- All dates as `LocalDate` or `Instant` — never `String` for internal use
- Do not modify the MCP server — it stays as TypeScript