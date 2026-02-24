# VAT System - Claude Code Master Context

## Project Overview
Danish VAT system with ViDA (VAT in the Digital Age) compliance.
Built using a multi-agent architecture orchestrated via Claude Code.

## Architecture Principles
- **Immutability**: VAT records are never deleted, only corrected via credit notes
- **Audit First**: Every state change is logged to the audit trail before taking effect
- **Rule Isolation**: All VAT business rules live in /packages/tax-engine with zero infrastructure dependencies
- **Anti-Corruption Layer**: All external integrations (SKAT, PEPPOL, VIES) are isolated in /packages/skat-client
- **ViDA Ready**: System is designed to support phased ViDA rollout (DRR, extended OSS, platform rules)

## Agent Responsibilities
| Agent | Folder | Responsibility |
|---|---|---|
| Architecture Agent | /agents/architecture-agent | System design, ADRs, data modeling |
| Domain Rules Agent | /agents/domain-rules-agent | VAT rules, classification, rate engine |
| Integration Agent | /agents/integration-agent | SKAT, PEPPOL, VIES, OSS integrations |
| Persistence Agent | /agents/persistence-agent | Data layer, immutable ledger, migrations |
| Reporting Agent | /agents/reporting-agent | VAT returns, SAF-T, ViDA DRR reports |
| Testing Agent | /agents/testing-agent | Test suite, compliance validation, SKAT sandbox |

## Package Responsibilities
- /packages/core-domain — Core entities: Transaction, Invoice, TaxCode, TaxPeriod, Counterparty
- /packages/tax-engine — Rate calculation, VAT classification, reverse charge logic
- /packages/invoice-validator — PEPPOL BIS 3.0 validation, Danish e-invoice rules
- /packages/skat-client — SKAT API client, VIES VAT number validation
- /packages/reporting — VAT return generation, SAF-T export, ViDA digital reports
- /packages/audit-trail — Immutable event log, correction chains, Bogføringsloven compliance

## Danish VAT Rules (Key References)
- Standard rate: 25% (MOMS)
- Zero-rated: exports, certain financial services
- Exempt: healthcare, education, insurance
- Reverse charge: applicable for certain B2B cross-border services
- Reporting: quarterly (default), monthly (large businesses >50M DKK turnover)
- Authority: SKAT (Skattestyrelsen) — https://skat.dk
- E-invoicing: NemHandel / PEPPOL BIS 3.0

## ViDA Compliance Roadmap
- 2025: Preparation phase — architecture must support future DRR
- 2028: Digital Reporting Requirements (DRR) — real-time transaction reporting
- 2025-2027: Extended One Stop Shop (OSS) for B2C
- 2025+: Platform economy deemed supplier rules

## Coding Conventions
- Language: TypeScript (strict mode)
- Testing: Vitest for unit tests, mandatory for all tax-engine logic
- No framework dependencies in /packages/core-domain or /packages/tax-engine
- All monetary values use integer arithmetic (øre, not kroner) to avoid floating point errors
- Dates use ISO 8601, timezone always UTC

## Key External Resources
- SKAT API: https://api.skat.dk
- SKAT Test Environment: https://api-sandbox.skat.dk
- PEPPOL BIS 3.0: https://docs.peppol.eu/poacc/billing/3.0/
- ViDA Directive: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52022PC0701
- VIES VAT Validation: https://ec.europa.eu/taxation_customs/vies/

## Inter-Agent Communication
Agents communicate via structured TypeScript interfaces defined in /packages/core-domain.
Never pass raw strings between agents — always use typed domain objects.
