# Documentation Audit Report - 2026-02-24

## Summary
- Files audited: 32
- Issues found: 33
- Critical issues: 17
- Fixed automatically: 10
- Requires manual fix: 23

## File Inventory
| File Path | Last Modified | Lines | Category |
|---|---:|---:|---|
| agents/architecture-agent/architecture-agent.md | 2026-02-24 13:26:10 | 204 | agent contract |
| agents/audit-agent/audit-agent.md | 2026-02-24 13:48:56 | 149 | agent contract |
| agents/business-analyst-agent/business-analyst-agent.md | 2026-02-24 11:09:23 | 163 | agent contract |
| agents/design-agent/design-agent.md | 2026-02-24 13:39:57 | 154 | agent contract |
| agents/domain-rules-agent/domain-rules-agent.md | 2026-02-24 11:12:23 | 112 | agent contract |
| agents/integration-agent/integration-agent.md | 2026-02-24 11:14:55 | 110 | agent contract |
| agents/persistence-agent/persistence-agent.md | 2026-02-24 11:15:42 | 131 | agent contract |
| agents/reporting-agent/reporting-agent.md | 2026-02-24 11:16:18 | 88 | agent contract |
| agents/testing-agent/testing-agent.md | 2026-02-24 11:17:15 | 141 | agent contract |
| CLAUDE.md | 2026-02-24 13:39:37 | 181 | governance |
| docs/adr/ADR-001-immutable-event-ledger.md | 2026-02-24 08:26:27 | 14 | ADR |
| docs/adr/ADR-002-technology-stack.md | 2026-02-24 10:58:15 | 86 | ADR |
| docs/adr/ADR-003-jurisdiction-plugin-architecture.md | 2026-02-24 13:38:14 | 124 | ADR |
| docs/adr/ADR-004-java-patterns.md | 2026-02-24 13:39:05 | 134 | ADR |
| docs/analysis/dk-vat-rules-validated.md | 2026-02-24 13:07:00 | 278 | analysis |
| docs/analysis/expert-review-questions.md | 2026-02-24 13:08:59 | 107 | analysis |
| docs/analysis/implementation-risk-register.md | 2026-02-24 13:07:58 | 61 | analysis |
| docs/analysis/README.md | 2026-02-24 13:15:46 | 23 | readme |
| docs/diagrams/01-c4-system-context.md | 2026-02-24 13:47:15 | 31 | diagram |
| docs/diagrams/02-c4-container.md | 2026-02-24 13:47:36 | 42 | diagram |
| docs/diagrams/03-entity-relationship.md | 2026-02-24 13:48:12 | 86 | diagram |
| docs/diagrams/04-sequence-vat-filing.md | 2026-02-24 13:48:45 | 106 | diagram |
| docs/domain-model/core-entities.md | 2026-02-24 13:36:26 | 239 | domain model |
| mcp-server/README.md | 2026-02-24 09:43:14 | 81 | readme |
| packages/audit-trail/README.md | 2026-02-24 08:26:27 | 2 | readme |
| packages/core-domain/README.md | 2026-02-24 08:26:27 | 2 | readme |
| packages/invoice-validator/README.md | 2026-02-24 08:26:27 | 2 | readme |
| packages/reporting/README.md | 2026-02-24 08:26:27 | 2 | readme |
| packages/skat-client/README.md | 2026-02-24 08:26:27 | 2 | readme |
| packages/tax-engine/README.md | 2026-02-24 08:26:27 | 2 | readme |
| README.md | 2026-02-24 09:41:09 | 2 | readme |
| ROLE_CONTEXT_POLICY.md | 2026-02-24 13:19:31 | 150 | governance |

## Critical Issues (must fix before next agent run)
| File | Issue | Recommended Fix |
|---|---|---|
| CLAUDE.md | Agent table and project structure do not list all existing agent folders (`business-analyst`, `design`, `audit`). | Update tables/structure to match disk state. |
| CLAUDE.md | Filing cadence summary omits `SEMI_ANNUAL` and thresholds are incomplete. | Include all three tiers and threshold boundaries. |
| CLAUDE.md vs code | "Last Agent Session" is not the most recent activity in repo. | Update session block at end of run. |
| ROLE_CONTEXT_POLICY.md vs repo | Container/Kubernetes requirements are mandatory but repo currently has no Dockerfiles, no k8s manifests, and no root `docker-compose.yml`. | Add required infra artifacts or mark as blocker with owner/date. |
| agents/domain-rules-agent/domain-rules-agent.md | References non-existent paths (`.../jurisdictions/dk`, multiple missing analysis docs). | Correct paths to existing directories/files. |
| agents/reporting-agent/reporting-agent.md | References non-existent domain types (`TaxReturn`, `Invoice`, `ReportFormatter`). | Align to existing `VatReturn` model and current interfaces. |
| agents/integration-agent/integration-agent.md | References non-existent core interfaces (`AuthorityApiClient`, etc.) and `Invoice`. | Update contract to current core-domain API. |
| agents/testing-agent/testing-agent.md | References missing analysis file (`analysis/08-scenario-universe-coverage-matrix-dk.md`). | Replace with existing analysis sources or add missing source file. |
| agents/persistence-agent/persistence-agent.md | References non-existent entities (`AuditEvent`, `TaxReturn`, `ReturnStatus`, `Invoice`). | Align contract with actual domain model names/types. |
| docs/adr/ADR-001-immutable-event-ledger.md | Missing required ADR section: `Alternatives Considered`. | Add alternatives section. |
| docs/adr/ADR-004-java-patterns.md | Does not follow required ADR section structure consistently (no explicit top-level `Alternatives Considered`). | Add required section headers. |
| docs/domain-model/core-entities.md vs code | ER diagram states `Counterparty -> VatReturn` relationship, but `VatReturn` has no counterparty field. | Fix ER relationship to match implemented model. |
| docs/domain-model/core-entities.md vs code | Filing threshold text says `<5M` semi-annual and `5M-50M` quarterly; DK plugin implementation uses `>5M` for quarterly (5M exact falls semi-annual). | Resolve by correcting code or adjusting docs after product decision. |
| docs/analysis/dk-vat-rules-validated.md vs CLAUDE.md | ViDA timeline differs (`2030` in analysis vs `2028` in CLAUDE). | Reconcile to a single timeline with cited source. |
| Module README coverage | Missing required READMEs in runnable modules (`core-domain`, `tax-engine`, `persistence`, `api`, `skat-client`). | Add complete README files per policy template. |

## Minor Issues (fix when convenient)
| File | Issue | Recommended Fix |
|---|---|---|
| ROLE_CONTEXT_POLICY.md | Escaped markdown/backslash artifacts reduce readability. | Normalize markdown formatting. |
| mcp-server/README.md | States cadence `half_yearly`, while core enum uses `SEMI_ANNUAL`. | Update wording for consistency. |
| packages/*/README.md and root README.md | Placeholder-level documentation (2 lines). | Expand or mark as archived legacy tree. |
| docs/analysis/README.md | States semi_annual "must be added" but already present in code. | Update stale finding text. |
| CLAUDE.md | Project structure block omits `docs/diagrams` and `docs/audit`. | Add current folders. |
| ADR-002 text | Mentions six-module build including mcp-server though Gradle includes five Java modules only. | Clarify mcp-server is outside Gradle multi-module. |

## Inconsistencies Found
| File A | File B | Contradiction |
|---|---|---|
| CLAUDE.md | docs/analysis/dk-vat-rules-validated.md | ViDA DRR date mismatch (2028 vs 2030 full compliance). |
| CLAUDE.md | repository tree | Missing agent folders and docs folders in documented structure. |
| docs/domain-model/core-entities.md | core-domain/src/main/java/com/netcompany/vat/coredomain/VatReturn.java | ER relation implies `Counterparty` link not present in model. |
| docs/domain-model/core-entities.md | core-domain/src/main/java/com/netcompany/vat/coredomain/dk/DkJurisdictionPlugin.java | Filing threshold boundary at exactly 5M DKK inconsistent. |
| docs/analysis/README.md | core-domain/src/main/java/com/netcompany/vat/coredomain/FilingCadence.java | README says `SEMI_ANNUAL` missing, code already includes it. |
| mcp-server/README.md | core-domain/src/main/java/com/netcompany/vat/coredomain/FilingCadence.java | Uses `half_yearly` wording vs `SEMI_ANNUAL` enum. |

## Files Verified as Correct
| File | Status |
|---|---|
| docs/analysis/implementation-risk-register.md | PASS - 25 risks, each with likelihood/impact/mitigation |
| docs/analysis/expert-review-questions.md | PASS - each question includes risk, assumption, uncertainty, priority |
| docs/adr/ADR-002-technology-stack.md | PASS - required ADR structure present and Java stack aligned |
| docs/adr/ADR-003-jurisdiction-plugin-architecture.md | PASS - required ADR structure present; references valid |
| agents/audit-agent/audit-agent.md | PASS - clear scope, checklist, and constraints |

## Fixed Automatically
- Added `Alternatives Considered` section to `docs/adr/ADR-001-immutable-event-ledger.md`.
- Updated `CLAUDE.md` Agent Responsibilities to include all current agent folders.
- Updated `CLAUDE.md` filing cadence summary to include SEMI_ANNUAL/QUARTERLY/MONTHLY tiers.
- Updated `CLAUDE.md` project structure block to include existing agent folders and docs folders.
- Added missing module READMEs: `core-domain/README.md`, `tax-engine/README.md`, `persistence/README.md`, `api/README.md`, `skat-client/README.md`.
- Updated `CLAUDE.md` Last Agent Session for this audit run.

## Requires Manual Fix
- Reconcile ViDA timeline across `CLAUDE.md` and analysis docs against authoritative source.
- Resolve exact 5M DKK filing cadence boundary in implementation vs docs.
- Decide whether to normalize or retire legacy `packages/*` documentation tree.
- Refactor outdated agent contracts that reference non-existent domain types and files.
- Implement missing Docker/Kubernetes artifacts required by policy.

## Recommended Fixes
1. Align all agent contracts with current `core-domain` type names and existing file paths.
2. Resolve filing cadence threshold boundary in code and documentation together.
3. Reconcile ViDA timeline dates with cited primary sources.
4. Complete infrastructure documentation + manifests (`Dockerfile`, `docker-compose.yml`, `/infrastructure/k8s/*`).
5. Consolidate or remove stale `packages/*` doc tree to avoid dual-source drift.
