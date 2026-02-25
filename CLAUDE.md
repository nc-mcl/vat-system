# VAT System - Claude Code Master Context

## Project Overview
Multi-jurisdiction VAT system with ViDA (VAT in the Digital Age) compliance.
Danish VAT is the first jurisdiction plugin; the architecture is designed to be jurisdiction-agnostic.
Built using a multi-agent architecture orchestrated via Claude Code.

## Project Phasing
- **Phase 1 (MVP):** Core Danish VAT system â€” rates, filing, SKAT integration, audit trail
- **Phase 2 (ViDA):** Digital Reporting Requirements (DRR), extended OSS, platform economy rules
- **Phase 3 (Multi-jurisdiction):** Plug in additional EU countries (Norway, Germany, etc.) with zero core changes

## Architecture Principles
- **Jurisdiction-Agnostic Core**: All core entities and interfaces are country-neutral. Danish VAT is a "jurisdiction plugin" â€” adding a new country requires only a new plugin, never changes to core.
- **Plugin Architecture**: Each jurisdiction implements the `JurisdictionPlugin` interface (tax rates, validation rules, filing schedules, report formats, authority API client, ViDA readiness flag).
- **Immutability**: VAT records are never deleted, only corrected via credit notes and new events.
- **Audit First**: Every state change is logged to the audit trail before taking effect.
- **Rule Isolation**: All VAT business rules live in /tax-engine with zero infrastructure dependencies.
- **Anti-Corruption Layer**: All external integrations (SKAT, PEPPOL, VIES) are isolated in /skat-client.
- **ViDA Ready**: System is designed to support phased ViDA rollout (DRR, extended OSS, platform rules).

## Plugin Architecture Pattern
A `JurisdictionPlugin` is a self-contained module that provides:
1. **Tax rates** â€” standard, reduced, zero, exempt categories
2. **Validation rules** â€” field-level and cross-field rules against authority requirements
3. **Filing schedules** â€” cadence (monthly/quarterly/annual), deadlines, and thresholds
4. **Report formats** â€” authority-specific XML/JSON schemas (e.g. SKAT rubrik format)
5. **Authority API client** â€” integration adapter (e.g. SKAT for DK, Skatteetaten for NO)
6. **ViDA readiness flag** â€” whether this jurisdiction has active ViDA obligations

Adding a new country = new plugin only. Zero changes to core packages.

## Agent Responsibilities
| Agent | Folder | Responsibility |
|---|---|---|
| Architecture Agent | /agents/architecture-agent | System design, ADRs, data modeling |
| Business Analyst Agent | /agents/business-analyst-agent | Rule research, source validation, risk register |
| Domain Rules Agent | /agents/domain-rules-agent | VAT rules, classification, rate engine |
| Integration Agent | /agents/integration-agent | SKAT, PEPPOL, VIES, OSS integrations |
| Persistence Agent | /agents/persistence-agent | Data layer, immutable ledger, migrations |
| Reporting Agent | /agents/reporting-agent | VAT returns, SAF-T, ViDA DRR reports |
| Testing Agent | /agents/testing-agent | Test suite, compliance validation, SKAT sandbox |
| Design Agent | /agents/design-agent | Mermaid architecture/domain diagrams |
| Audit Agent | /agents/audit-agent | Documentation accuracy and consistency audit |

## Module Responsibilities (Gradle multi-module)
- /core-domain â€” Core entities and `JurisdictionPlugin` interface; jurisdiction plugins live here
- /tax-engine â€” Rate calculation, VAT classification, reverse charge logic (no infra dependencies)
- /persistence â€” JOOQ data layer, Flyway migrations, immutable event ledger, audit trail
- /api â€” Spring Boot REST API, Bean Validation, request/response DTOs
- /skat-client â€” SKAT API client, VIES VAT number validation, PEPPOL e-invoice integration

## Danish VAT Rules (Key References)
- Standard rate: 25% (MOMS)
- Zero-rated: exports, intra-EU supplies (goods), certain financial services
- Exempt: healthcare, education, insurance (no VAT recovery)
- Reverse charge: B2B cross-border services (buyer accounts for VAT)
- Filing cadence: semi-annual (<5M DKK), quarterly (5M-50M DKK), monthly (>50M DKK annual turnover)
- Filing rubriks: A (EU purchases â€” goods/services), B (EU sales â€” goods/services)
- Net VAT = output VAT âˆ’ deductible input VAT; result is either payable or claimable
- Authority: SKAT (Skattestyrelsen) â€” https://skat.dk
- E-invoicing: NemHandel / PEPPOL BIS 3.0
- Bookkeeping law: BogfÃ¸ringsloven â€” records immutable for 5 years

## ViDA Compliance Roadmap
- 2026 (now): Preparation phase â€” architecture must support future DRR
- 2028: Digital Reporting Requirements (DRR) â€” real-time transaction reporting to tax authority
- 2025â€“2027: Extended One Stop Shop (OSS) for B2C cross-border
- 2025+: Platform economy deemed supplier rules

## Domain Knowledge Gaps (identified from MCP validation)
- Filing rubrik field names are authority-prescribed (rubrikA/rubrikB for EU transactions)
- `claimAmount` and `resultType` (payable vs claimable) are derived, not stored
- `periodDays` must be validated against the expected cadence window
- `totalInternationalValue` is a derived aggregate of all EU rubrik values

## Coding Conventions
- Language: Java 21 (records, sealed interfaces, pattern matching, virtual threads)
- Framework: Spring Boot 3.3 (Spring MVC, Spring Data JOOQ, Bean Validation)
- Build: Gradle 8.x with Kotlin DSL (`build.gradle.kts`), multi-module layout
- Database access: JOOQ (typesafe SQL generation from schema) + Flyway (versioned migrations)
- Validation: Jakarta Bean Validation 3.0 (`@NotNull`, `@Valid`, custom `ConstraintValidator`)
- Testing: JUnit 5 + Mockito + Testcontainers (PostgreSQL) â€” mandatory for all tax-engine logic
- Monetary values: `long` representing smallest currency unit (Ã¸re for DKK) â€” never `double` or `float`
- Monetary arithmetic uses exact integer operations; rates in basis points (2500 = 25.00%)
- Dates: `java.time.LocalDate` / `java.time.Instant` (UTC); ISO 8601 on the wire
- Base package: `com.netcompany.vat`
- No framework dependencies in /core-domain or /tax-engine â€” pure Java only
- All inter-agent communication uses typed domain objects from /core-domain

## MCP Server (separate tool â€” remains TypeScript)
The MCP server under /mcp-server is a standalone Claude Code tool written in TypeScript/Node.js.
It is NOT part of the Java backend. It provides business analyst and architect context to Claude agents.
Do not apply Java conventions to /mcp-server.

## Key External Resources
- SKAT API: https://api.skat.dk
- SKAT Test Environment: https://api-sandbox.skat.dk
- PEPPOL BIS 3.0: https://docs.peppol.eu/poacc/billing/3.0/
- ViDA Directive: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52022PC0701
- VIES VAT Validation: https://ec.europa.eu/taxation_customs/vies/

## Mandatory Constraints (All Agents)

> These constraints are non-negotiable. Every agent must follow them.
> Full details in `ROLE_CONTEXT_POLICY.md` â€” read it before starting work.

### Open Source Only
All dependencies must use permissive open source licenses (Apache 2.0, MIT, BSD).
No commercial, GPL, or AGPL dependencies. Verify every dependency before adding it.
Pre-approved dependencies are listed in `ROLE_CONTEXT_POLICY.md`.

### Containers and Kubernetes
Every runnable service must have:
- A multi-stage `Dockerfile` using minimal Alpine base images
- Kubernetes manifests in `/infrastructure/k8s/<service-name>/`
- Health check endpoints (`/actuator/health/liveness` and `/actuator/health/readiness`)
- Explicit resource limits on all pods
A `docker-compose.yml` must exist in the project root for local development.

### Agent Handoff Protocol
Every agent must before finishing:
1. Update `CLAUDE.md` with a "Last Agent Session" section
2. Update the README of every component it touched
3. Verify the next agent has everything it needs
4. Print a structured Handoff Summary in chat

### README Standards
Every directory with runnable code must have a README covering:
what it does, how to build, how to run, how to test, and all environment variables.

## Technology Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3.3
- **Build:** Gradle multi-module
- **Database:** PostgreSQL + Flyway + JOOQ (open source edition)
- **Validation:** Bean Validation (Jakarta)
- **Testing:** JUnit 5 + Mockito + Testcontainers
- **API:** Spring Web (REST)
- **Observability:** Micrometer + OpenTelemetry + structured JSON logging
- **Containers:** Docker (eclipse-temurin:21-jre-alpine)
- **Orchestration:** Kubernetes
- **MCP Server:** TypeScript / Node.js (separate tool, not part of backend)
- **Base package:** `com.netcompany.vat`

## Project Structure
```
/vat-system
  CLAUDE.md                        â† Master context (this file)
  ROLE_CONTEXT_POLICY.md           â† Mandatory agent constraints
  docker-compose.yml               â† Local development
  settings.gradle.kts
  build.gradle.kts
  /agents                          â† Agent operating contracts
    /architecture-agent
    /business-analyst-agent
    /domain-rules-agent
    /persistence-agent
    /integration-agent
    /reporting-agent
    /testing-agent
    /design-agent
    /audit-agent
  /core-domain                     â† Pure Java domain model
  /tax-engine                      â† Pure Java business logic
  /persistence                     â† JOOQ + Flyway + repositories
  /api                             â† Spring Boot REST API
  /skat-client                     â† SKAT API integration
  /mcp-server                      â† TypeScript MCP server
  /docs
    /adr                           â† Architecture Decision Records
    /domain-model
    /analysis                      â† Validated VAT domain knowledge
    /diagrams
    /audit
  /infrastructure
    /k8s                           â† Kubernetes manifests
      /cluster                     â† Namespace, ingress, secrets template
      /api
      /persistence
      /mcp-server
    /db
      /migrations                  â† Flyway SQL migrations
      /seeds                       â† Test data
```

## Last Agent Session

**Agent:** CI Fix
**Date:** 2026-02-25
**Next agent can proceed:** yes
**Blockers for next agent:** none

### What was done

- **Verified `.github/workflows/ci.yml` action references** — confirmed all fixes were already applied in commit `3d2495e` (Persistence Agent). Current correct state:
  - `dorny/test-reporter@v2` (was `dorny/tests-reporter@v1` — typo + version)
  - `actions/checkout@v6` (both jobs)
  - `actions/setup-java@v5` (both jobs)
  - `docker/build-push-action@v6`
  - `docker/setup-buildx-action@v3` — already current
- **All 68 tax-engine tests still pass** (`BUILD SUCCESSFUL`)
- **Documentation updated:** CLAUDE.md, README.md status comment, session-log.md

### What the next agent needs to know

1. CI pipeline will now resolve correctly — the `dorny/tests-reporter` 404 that was breaking the `Publish test results` step is fixed.
2. No Java source was changed; no schema changes; no new dependencies.
3. On Windows, `GRADLE_OPTS` may be pre-set to `"-Dfile.encoding=UTF-8"` (with literal quotes) which breaks `./gradlew`. Unset it before invoking: `unset GRADLE_OPTS && ./gradlew ...`

### Recommended next agent
**API Agent** — implement Spring Boot REST endpoints using the persistence layer (see Persistence Agent session above). Everything is in place.

---

## Previous Last Agent Session

**Agent:** Persistence Agent
**Date:** 2026-02-25
**Next agent can proceed:** yes
**Blockers for next agent:** none

### What was done

- **Flyway migrations** (3 files in both `infrastructure/db/migrations/` and `persistence/src/main/resources/db/migration/`):
  - `V001__create_schema.sql` — `counterparties`, `tax_periods`, `transactions`, `vat_returns`, `corrections`
  - `V002__create_audit_log.sql` — immutable `audit_log` (BIGSERIAL PK, JSONB payload, Bogføringsloven comment)
  - `V003__create_indexes.sql` — composite indexes for API query patterns
- **`persistence/build.gradle.kts`** — added `jackson-databind`, `logstash-logback-encoder:7.4` (DevOps handoff requirement), registered `src/main/generated` as source set for future JOOQ code gen
- **Repository interfaces** — `TaxPeriodRepository`, `TransactionRepository`, `VatReturnRepository`, `CounterpartyRepository`, `CorrectionRepository` in `com.netcompany.vat.persistence.repository`
- **JOOQ implementations** — `JooqXxx` classes using dynamic DSL (`DSL.table()`, `DSL.field()`); no code gen needed to compile
- **Audit** — `AuditEvent` record + `AuditLogger` interface + `JooqAuditLogger` (append-only, no delete path)
- **Mappers** — 5 pure-function mapper classes converting JOOQ `Record` → domain objects; `VatReturnMapper` handles JSONB deserialization of `jurisdictionFields`
- **`PersistenceConfiguration`** — `@Configuration` class that registers all repos and `AuditLogger` as Spring beans; `@ConditionalOnMissingBean(ObjectMapper)` fallback
- **`application-persistence.yml`** — DataSource, Flyway, JOOQ dialect defaults with env-variable overrides
- **Integration tests** (5 IT classes) — `FlywayMigrationIT`, `TaxPeriodRepositoryIT`, `TransactionRepositoryIT`, `VatReturnRepositoryIT`, `AuditLoggerIT`; all skip gracefully when Docker is not accessible from the Java process (Windows Docker Desktop issue); they run in CI
- **`persistence/README.md`** — full documentation including Docker Desktop setup instructions for Windows
- **`~/.testcontainers.properties`** — created with `docker.host=npipe:////./pipe/dockerDesktopLinuxEngine` for future use when Docker Desktop TCP is enabled
- **All 68 existing tax-engine tests still pass** (verified with `./gradlew :tax-engine:test`)

### What the next agent (API Agent) needs to know

1. **Repository beans** — all 5 repository interfaces are Spring beans registered via `PersistenceConfiguration`. Import or component-scan `com.netcompany.vat.persistence` in the API module.
2. **`AuditLogger` bean** — available as a Spring bean; must be called before every state-changing operation.
3. **Flyway** — migrations are in `persistence/src/main/resources/db/migration/`; they run automatically on Spring Boot startup when `spring.flyway.locations=classpath:db/migration` is set (see `application-persistence.yml`).
4. **DataSource** — configured via `DB_URL`, `DB_USER`, `DB_PASSWORD` env variables (defaults match `docker-compose.yml`).
5. **`logstash-logback-encoder:7.4`** — added to `persistence/build.gradle.kts`. The `api` module also needs it for structured logging; add to `api/build.gradle.kts`.
6. **JOOQ code generation** — repositories use dynamic DSL; add `nu.studer.jooq` v9 plugin to `persistence/build.gradle.kts` to generate type-safe classes when schema stabilises.
7. **Integration tests require Docker TCP** — on Windows with Docker Desktop, enable "Expose daemon on tcp://localhost:2375" for local IT runs; CI (Linux) works with default socket.
8. **`TaxPeriodRepository.save(period, filingDeadline)`** — takes an explicit `LocalDate filingDeadline` because the domain `TaxPeriod` record doesn't store it; compute via `JurisdictionPlugin.calculateFilingDeadline()` at the service layer before calling the repository.

### Recommended next agent
**API Agent** — implement Spring Boot REST endpoints using the repository interfaces. The persistence layer is complete and all beans are available.

---

## Previous Last Agent Session

**Agent:** DevOps Agent
**Date:** 2026-02-25
**Next agent can proceed:** yes
**Blockers for next agent:** none

### What was done
- **Fix 1 — `.gitignore`**: Updated to include `**/build/` and `!gradle/wrapper/gradle-wrapper.properties`; removed all tracked `.gradle/` and `build/` files from git index via `git rm --cached`.
- **Fix 2 — `devcontainer.json`**: Added `"postStartCommand": "chmod +x /workspace/gradlew"` and `"terminal.integrated.defaultProfile.linux": "bash"` setting.
- **Phase 1 (Dev Container)**: All 3 `.devcontainer/` files verified correct (Dockerfile, docker-compose.yml, devcontainer.json). `gradlew DEFAULT_JVM_OPTS` was already clean.
- **Phase 2 (Docker)**: Created `api/Dockerfile` — multi-stage build (eclipse-temurin:21-jdk-jammy builder → eclipse-temurin:21-jre-alpine runtime), non-root `vatuser`, HEALTHCHECK, container-aware JVM flags. Created root `docker-compose.yml` (api + postgres + adminer).
- **Phase 3 (CI/CD)**: Created `.github/workflows/ci.yml` — `test` job (Gradle build + postgres service container + JUnit test reporter) + `build-image` job (Docker Buildx, main branch only, GHA cache).
- **Phase 4 (Kubernetes)**: Created all 8 manifests — namespace, secrets-template, api/deployment, api/service, api/configmap, api/hpa, postgres/statefulset, postgres/service. All pass `kubectl apply --dry-run=client`.
- **Phase 5 (Observability)**: Created `api/src/main/resources/logback-spring.xml` — structured JSON logging (LogstashEncoder) for non-local profiles; human-readable console for `local` profile.
- **infrastructure/README.md**: Created with dev container setup, local docker-compose usage, Kubernetes deployment steps, CI/CD overview, and add-new-service guide.

### What the next agent needs to know
1. **Kubernetes secrets** — `vat-secrets` (db-password, skat-api-key) must be created manually via `kubectl create secret`; the template at `infrastructure/k8s/cluster/secrets-template.yaml` shows the shape.
2. **logstash-logback-encoder dependency** — `api/build.gradle.kts` must add `net.logstash.logback:logstash-logback-encoder` when the API module is built out. The Persistence Agent or API agent should add this when they add Spring Boot dependencies.
3. **`infrastructure/db/migrations/`** exists but is empty — Flyway migration scripts go here (Persistence Agent's job).
4. **Docker image build** will fail until `api` has a `bootJar` task (requires Spring Boot plugin in `api/build.gradle.kts`). The `api/Dockerfile` is ready; only the Gradle build wiring is missing.
5. **68 tests still pass** — no Java source was modified.

### Recommended next agent
**Persistence Agent** — implement JOOQ/Flyway data layer: add `api/build.gradle.kts` Spring Boot plugin, write Flyway migrations in `infrastructure/db/migrations/`, implement repositories. The DevOps scaffolding is in place.

---

## Previous Last Agent Session

**Agent:** Pre-Persistence Fixup (Codex)
**Date:** 2026-02-24
**Next agent can proceed:** yes
**Blockers for next agent:** none

### What was done
- **Fix 1 — `Transaction.vatAmount()` guard**: Added early return of `MonetaryAmount.ZERO` when
  `classification.rateInBasisPoints() < 0` (EXEMPT / OUT_OF_SCOPE). Root cause resolved in
  `core-domain/Transaction.java`; `VatReturnAssembler`'s switch guard is now a belt-and-suspenders
  defence rather than the only guard.
- **Fix 2 — Package rename**: Renamed `com.netcompany.vat.coredomain` → `com.netcompany.vat.domain`
  across all Java files in `core-domain/` (package declarations) and `tax-engine/` (imports).
  Directory structure moved from `coredomain/` to `domain/`. CLAUDE.md base-package reference is now
  accurate.
- **Fix 3 — DK filing deadlines**: Replaced single "10th of following month" rule with cadence-aware
  logic in `DkJurisdictionPlugin.calculateFilingDeadline()`:
  - `MONTHLY` → 25th of the following month
  - `QUARTERLY / SEMI_ANNUAL / ANNUAL` → 1st of the 3rd month after period end
  Updated `FilingPeriodCalculatorTest` deadline assertions and overdue-check instants to match.
- **All 68 tests pass**: `BUILD SUCCESSFUL` — 68 tests, 0 failures, 0 errors.
- **Gradle wrapper jar replaced**: The pre-existing jar was truncated (42 KB); replaced with a valid
  Gradle 8.10.2 wrapper jar downloaded from GitHub.

### What the next agent needs to know
1. **Base package is now `com.netcompany.vat.domain`** — the rename is complete; no further cleanup needed.
2. **`Transaction.vatAmount()` is safe for all tax codes** — the guard is in place; the assembler's
   switch guard remains as defence-in-depth.
3. **Filing deadlines are cadence-aware** — downstream tests/fixtures must use the new deadline dates
   (e.g. Q1 QUARTERLY → 2026-06-01, not 2026-04-10).
4. **Rubrik goods/services split** still requires `transactionType` + `counterpartyCountry` on
   `Transaction` for full EU routing. Advisory item — not a blocker.
5. **Build**: Run via PowerShell with `JAVA_HOME` and `GRADLE_USER_HOME` set explicitly (see
   Domain Rules Agent notes). The Gradle 8.10.2 distribution is cached at
   `C:\Temp\gradle-user-home\wrapper\dists\gradle-8.10.2-bin\`.

### Recommended next agent
**Persistence Agent** — implement the JOOQ/Flyway data layer, repositories, and immutable event ledger
using the domain types now available from `core-domain` (`com.netcompany.vat.domain`) and `tax-engine`.

---

## Previous Agent Session

**Agent:** Domain Rules Agent
**Date:** 2026-02-24

### What was done
- Implemented all 8 tax-engine source classes: `RateResolver`, `VatCalculator`, `ReverseChargeEngine`,
  `ExemptionClassifier`, `FilingPeriodCalculator`, `VatReturnAssembler`, `TaxEngine` (facade),
  and `ReverseChargeResult` (value type). `VatResult` was already created by the Architecture Agent.
- `Result<T>` was already in `core-domain` — used directly, no duplicate created.
- 68 JUnit 5 tests written across 6 test classes — all pass (`BUILD SUCCESSFUL`).
- Created Gradle wrapper scripts (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) — project had none.
- Updated `tax-engine/README.md` with full component documentation and known gaps.

---

## Previous Agent Session

**Agent:** Design Agent
**Date:** 2026-02-24

### What was done
- Created all 9 Mermaid diagram files in `docs/diagrams/`
- Diagrams are based strictly on actual Java source in `core-domain/`, validated domain knowledge in `docs/analysis/dk-vat-rules-validated.md`, and ADR-003
- All known gaps from the BA analysis are marked visually in the relevant diagrams (⚠️ yellow / 🔴 red)

---

## Previous Agent Session

**Agent:** Documentation Alignment (Codex)
**Date:** 2026-02-24

### What was done
- Updated stale operating contracts in `agents/domain-rules-agent`, `agents/integration-agent`, `agents/persistence-agent`, `agents/reporting-agent`, and `agents/testing-agent`
- Removed references to non-existent files/types and aligned instructions to current repository reality
- Preserved role scope while making task lists executable against existing modules and documents
