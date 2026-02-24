# VAT System - Claude Code Master Context

## Project Overview
Multi-jurisdiction VAT system with ViDA (VAT in the Digital Age) compliance.
Danish VAT is the first jurisdiction plugin; the architecture is designed to be jurisdiction-agnostic.
Built using a multi-agent architecture orchestrated via Claude Code.

## Project Phasing
- **Phase 1 (MVP):** Core Danish VAT system — rates, filing, SKAT integration, audit trail
- **Phase 2 (ViDA):** Digital Reporting Requirements (DRR), extended OSS, platform economy rules
- **Phase 3 (Multi-jurisdiction):** Plug in additional EU countries (Norway, Germany, etc.) with zero core changes

## Architecture Principles
- **Jurisdiction-Agnostic Core**: All core entities and interfaces are country-neutral. Danish VAT is a "jurisdiction plugin" — adding a new country requires only a new plugin, never changes to core.
- **Plugin Architecture**: Each jurisdiction implements the `JurisdictionPlugin` interface (tax rates, validation rules, filing schedules, report formats, authority API client, ViDA readiness flag).
- **Immutability**: VAT records are never deleted, only corrected via credit notes and new events.
- **Audit First**: Every state change is logged to the audit trail before taking effect.
- **Rule Isolation**: All VAT business rules live in /tax-engine with zero infrastructure dependencies.
- **Anti-Corruption Layer**: All external integrations (SKAT, PEPPOL, VIES) are isolated in /skat-client.
- **ViDA Ready**: System is designed to support phased ViDA rollout (DRR, extended OSS, platform rules).

## Plugin Architecture Pattern
A `JurisdictionPlugin` is a self-contained module that provides:
1. **Tax rates** — standard, reduced, zero, exempt categories
2. **Validation rules** — field-level and cross-field rules against authority requirements
3. **Filing schedules** — cadence (monthly/quarterly/annual), deadlines, and thresholds
4. **Report formats** — authority-specific XML/JSON schemas (e.g. SKAT rubrik format)
5. **Authority API client** — integration adapter (e.g. SKAT for DK, Skatteetaten for NO)
6. **ViDA readiness flag** — whether this jurisdiction has active ViDA obligations

Adding a new country = new plugin only. Zero changes to core packages.

## Agent Responsibilities
| Agent | Folder | Responsibility |
|---|---|---|
| Architecture Agent | /agents/architecture-agent | System design, ADRs, data modeling |
| Domain Rules Agent | /agents/domain-rules-agent | VAT rules, classification, rate engine |
| Integration Agent | /agents/integration-agent | SKAT, PEPPOL, VIES, OSS integrations |
| Persistence Agent | /agents/persistence-agent | Data layer, immutable ledger, migrations |
| Reporting Agent | /agents/reporting-agent | VAT returns, SAF-T, ViDA DRR reports |
| Testing Agent | /agents/testing-agent | Test suite, compliance validation, SKAT sandbox |

## Module Responsibilities (Gradle multi-module)
- /core-domain — Core entities and `JurisdictionPlugin` interface; jurisdiction plugins live here
- /tax-engine — Rate calculation, VAT classification, reverse charge logic (no infra dependencies)
- /persistence — JOOQ data layer, Flyway migrations, immutable event ledger, audit trail
- /api — Spring Boot REST API, Bean Validation, request/response DTOs
- /skat-client — SKAT API client, VIES VAT number validation, PEPPOL e-invoice integration

## Danish VAT Rules (Key References)
- Standard rate: 25% (MOMS)
- Zero-rated: exports, intra-EU supplies (goods), certain financial services
- Exempt: healthcare, education, insurance (no VAT recovery)
- Reverse charge: B2B cross-border services (buyer accounts for VAT)
- Filing cadence: quarterly (default), monthly (>50M DKK annual turnover)
- Filing rubriks: A (EU purchases — goods/services), B (EU sales — goods/services)
- Net VAT = output VAT − deductible input VAT; result is either payable or claimable
- Authority: SKAT (Skattestyrelsen) — https://skat.dk
- E-invoicing: NemHandel / PEPPOL BIS 3.0
- Bookkeeping law: Bogføringsloven — records immutable for 5 years

## ViDA Compliance Roadmap
- 2026 (now): Preparation phase — architecture must support future DRR
- 2028: Digital Reporting Requirements (DRR) — real-time transaction reporting to tax authority
- 2025–2027: Extended One Stop Shop (OSS) for B2C cross-border
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
- Testing: JUnit 5 + Mockito + Testcontainers (PostgreSQL) — mandatory for all tax-engine logic
- Monetary values: `long` representing smallest currency unit (øre for DKK) — never `double` or `float`
- Monetary arithmetic uses exact integer operations; rates in basis points (2500 = 25.00%)
- Dates: `java.time.LocalDate` / `java.time.Instant` (UTC); ISO 8601 on the wire
- Base package: `com.netcompany.vat`
- No framework dependencies in /core-domain or /tax-engine — pure Java only
- All inter-agent communication uses typed domain objects from /core-domain

## MCP Server (separate tool — remains TypeScript)
The MCP server under /mcp-server is a standalone Claude Code tool written in TypeScript/Node.js.
It is NOT part of the Java backend. It provides business analyst and architect context to Claude agents.
Do not apply Java conventions to /mcp-server.

## Key External Resources
- SKAT API: https://api.skat.dk
- SKAT Test Environment: https://api-sandbox.skat.dk
- PEPPOL BIS 3.0: https://docs.peppol.eu/poacc/billing/3.0/
- ViDA Directive: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52022PC0701
- VIES VAT Validation: https://ec.europa.eu/taxation_customs/vies/
