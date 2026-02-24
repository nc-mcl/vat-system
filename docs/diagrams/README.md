# Diagrams — VAT System Visual Documentation

This directory contains all Mermaid diagrams for the VAT system. Every diagram is embedded in a Markdown file and renders natively in GitHub, GitLab, and any Mermaid-compatible viewer.

All diagrams are maintained by the **Design Agent** and must be kept in sync with the codebase and domain knowledge documents.

---

## Diagram Index

| # | File | What It Shows | Most Useful For | Update When |
|---|---|---|---|---|
| 01 | [01-c4-system-context.md](01-c4-system-context.md) | System boundary, all external actors and systems (SKAT, VIES, PEPPOL, OSS) | Stakeholders, architects, new team members | A new external system integration is added or removed |
| 02 | [02-c4-container.md](02-c4-container.md) | All deployable containers: REST API, Tax Engine (library), SKAT Client, PostgreSQL, MCP Server | Developers, architects, operations | A new container is added, or an existing container's responsibilities change |
| 03 | [03-entity-relationship.md](03-entity-relationship.md) | Core domain entities and their relationships (Transaction, TaxPeriod, VatReturn, Correction, Counterparty) | Developers, persistence engineers, domain modellers | Any new entity or field is added to `core-domain`; always keep in sync with Java records |
| 04 | [04-sequence-vat-filing.md](04-sequence-vat-filing.md) | End-to-end VAT return filing flow: transaction ingestion → draft assembly → SKAT submission → acceptance/rejection | Developers, QA, integration testers | Filing flow changes, new steps are added, or SKAT API contract changes |
| 05 | [05-dataflow-reverse-charge.md](05-dataflow-reverse-charge.md) | Decision tree for reverse charge applicability (cross-border, ML §46 domestic, B2B/B2C) | Developers, tax consultants, QA | DK reverse charge rules change; a new jurisdiction's reverse charge logic is added |
| 06 | [06-dataflow-vat-classification.md](06-dataflow-vat-classification.md) | Full VAT classification decision tree: STANDARD, ZERO_RATED, EXEMPT, REVERSE_CHARGE, OUT_OF_SCOPE | Developers, tax consultants, testing team | DK exemption rules change, new TaxCode is added, or rubrik mappings are updated |
| 07 | [07-kubernetes-deployment.md](07-kubernetes-deployment.md) | Kubernetes deployment architecture: namespace, workloads, services, config, secrets, HPA | Operations, DevOps, architects | A new service is deployed, resource limits change, or Kubernetes manifests are restructured |
| 08 | [08-jurisdiction-plugin-architecture.md](08-jurisdiction-plugin-architecture.md) | Plugin system: JurisdictionPlugin interface, DkJurisdictionPlugin, registry, future Norway/Germany placeholders | Architects, developers adding new jurisdictions, stakeholders | A new jurisdiction is added (Phase 3), or the JurisdictionPlugin interface changes |

---

## Diagram Standards

- All diagrams use **Mermaid** syntax (GitHub-compatible, Mermaid v10+)
- C4 diagrams use `C4Context` / `C4Container` blocks
- ERD uses `erDiagram`
- Sequence diagrams use `sequenceDiagram`
- Data flow and decision diagrams use `flowchart TD`
- Deployment diagrams use `graph TD`
- Every diagram file includes: title, description, last updated date, producing agent
- **Diagrams must reflect what actually exists** — never invent entities or flows

## Keeping Diagrams Accurate

Diagrams are **living documents**. They go stale when code changes without updating them. Each diagram's "Update When" column specifies the trigger.

Critical sources of truth to cross-check:
- `core-domain/src/main/java/` — entity fields and plugin interface
- `docs/domain-model/core-entities.md` — entity catalogue
- `docs/analysis/dk-vat-rules-validated.md` — validated DK VAT rules and gaps
- `docs/adr/ADR-003-jurisdiction-plugin-architecture.md` — plugin architecture decisions
- `infrastructure/k8s/` — Kubernetes manifests

## Gap Markers

Diagrams use visual markers for known gaps:
- ⚠️ **Yellow** — unverified or uncertain rule; safe to implement with caution
- ❌ / 🔴 **Red** — confirmed gap requiring expert review before implementation
- 🔄 **Blue dashed** — future/Phase 2-3 item not yet implemented

See `docs/analysis/dk-vat-rules-validated.md` section 9 for the full gap register.

---

*Last updated: 2026-02-24 by Design Agent*
