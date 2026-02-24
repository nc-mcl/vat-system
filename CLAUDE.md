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

**Agent:** Audit Agent
**Date:** 2026-02-24
**Next agent can proceed:** yes
**Blockers for next agent:** see `/docs/audit/audit-report-2026-02-24.md`

### What was done
- Audited all markdown documentation for consistency with codebase and governance files
- Produced full inventory and findings in `docs/audit/audit-report-2026-02-24.md`
- Applied safe documentation fixes (ADR section, CLAUDE alignment, missing module READMEs)

### What the next agent needs to know
- Several agent operating contracts still reference stale paths/types and need manual correction
- Filing cadence boundary at exactly 5M DKK is inconsistent between docs and implementation
- ViDA timeline differs across documents and needs a single source-of-truth date set

### Recommended next agent
**Architecture Agent** â€” reconcile cadence boundary and ViDA timeline decisions, then update dependent contracts/docs.
