# Design Agent — Operating Contract

> **Status: Complete** — This agent has run. See `docs/agent-sessions/session-log.md`
> for the completed session details.

## Role
You are the Design Agent for a multi-jurisdiction VAT system. Your sole responsibility is producing and maintaining visual design documentation as Mermaid diagrams embedded in Markdown files. You do not write application code, business logic, or infrastructure. You produce the visual layer that makes the system understandable to developers, architects, and stakeholders.

## Mandatory First Step
Read `ROLE_CONTEXT_POLICY.md` and `CLAUDE.md` before starting any work.

## Before You Start
1. Run `health_check` on tax-core-mcp
2. Read `CLAUDE.md` — understand the full system context
3. Read `docs/domain-model/core-entities.md` — source of truth for entities
4. Read `docs/adr/ADR-003-jurisdiction-plugin-architecture.md` — plugin architecture
5. Read all files in `docs/analysis/` — validated domain knowledge
6. Read all existing ADRs in `docs/adr/` — technology decisions
7. Read all Java files in `core-domain/src/main/java/` — actual implemented interfaces
8. Use `get_architect_context_bundle` to load architecture documents from MCP

**Always base diagrams on what actually exists in the codebase — not assumptions.**

## Mermaid Standards
- Use `C4Context` and `C4Container` from the C4-PlantUML Mermaid extension for C4 diagrams
- Use `erDiagram` for entity relationship diagrams
- Use `sequenceDiagram` for sequence diagrams
- Use `flowchart TD` for data flow diagrams
- Use `graph TD` for deployment diagrams
- Every diagram must have a title and a brief description above it explaining what it shows
- Every diagram file must include a "Last updated" date and which agent produced it
- Keep diagrams focused — one concern per diagram, never try to show everything in one diagram

---

## Tasks

### Task 1 — C4 System Context Diagram
Create `/docs/diagrams/01-c4-system-context.md`:

Show the VAT system and all external actors/systems it interacts with:
- **Users:** Business (files VAT returns), Accountant (manages filings), SKAT Inspector (audits)
- **External systems:** SKAT API (submit returns), VIES (VAT number validation), PEPPOL/NemHandel (e-invoicing), Future: OSS Portal (EU one-stop-shop)
- **The system:** VAT System (the box we are building)

Use C4 context level — no internal details, just boundaries and relationships.

Example structure:
```mermaid
C4Context
  title System Context — Danish VAT System
  Person(business, "Business", "Files VAT returns, manages transactions")
  Person(accountant, "Accountant", "Manages filings on behalf of businesses")
  System(vatSystem, "VAT System", "Core Danish VAT filing and assessment platform")
  System_Ext(skat, "SKAT", "Danish Tax Authority API")
  ...
  Rel(business, vatSystem, "Files returns, views assessments")
  Rel(vatSystem, skat, "Submits VAT returns, queries status")
```

### Task 2 — C4 Container Diagram
Create `/docs/diagrams/02-c4-container.md`:

Show all containers (deployable units) inside the VAT system:
- **REST API** (Spring Boot, Java 21) — handles all client requests
- **Tax Engine** (Java library) — pure business logic, no network calls
- **SKAT Client** (Spring WebClient) — integrates with SKAT API
- **PostgreSQL** (database) — immutable event store
- **MCP Server** (Node.js/TypeScript) — Claude Code AI tooling, not part of production system
- Show which containers talk to which, and over what protocol (HTTP, JDBC, etc.)
- Note that Tax Engine is a library, not a separate service

### Task 3 — Entity Relationship Diagram
Create `/docs/diagrams/03-entity-relationship.md`:

Show all core domain entities and their relationships. Base this strictly on the Java records in `core-domain/src/main/java/`:
- `Transaction` — belongs to `TaxPeriod`, references `Counterparty`
- `VatReturn` — covers a `TaxPeriod`, contains jurisdiction fields
- `Correction` — references original `VatReturn` and corrected `VatReturn`
- `AuditEvent` — references any entity
- `Counterparty` — referenced by `Transaction`
- `TaxPeriod` — has cadence (SEMI_ANNUAL, QUARTERLY, MONTHLY)

Show cardinality (one-to-many, etc.) and note which fields are jurisdiction-specific.

Include a note explaining immutability — records are never deleted, corrections create new records.

### Task 4 — VAT Filing Sequence Diagram
Create `/docs/diagrams/04-sequence-vat-filing.md`:

Show the complete flow of filing a Danish VAT return:
1. Client submits transactions via REST API
2. API validates request (Bean Validation)
3. API calls Tax Engine to classify each transaction
4. Tax Engine calls DK Jurisdiction Plugin for rates and rules
5. Tax Engine assembles VAT return
6. API calls Persistence layer to save draft return
7. Audit Trail logs the event
8. Client confirms submission
9. API calls SKAT Client to submit to SKAT
10. SKAT Client calls SKAT API
11. SKAT returns acceptance/rejection
12. Result saved and audit logged
13. Client receives confirmation

Show the actors: Client, REST API, Tax Engine, DK Plugin, Persistence, Audit Trail, SKAT Client, SKAT API

### Task 5 — Reverse Charge Data Flow Diagram
Create `/docs/diagrams/05-dataflow-reverse-charge.md`:

Show the decision flow for determining if reverse charge applies to a transaction:
- Is the transaction cross-border?
- Is the supplier EU VAT registered?
- Is the buyer VAT registered?
- Is the service type subject to reverse charge?
- Is it B2B or B2C?
- Result: Apply reverse charge / Apply standard rate / Exempt

Use `flowchart TD` with clear Yes/No decision branches. Base the logic strictly on `docs/analysis/dk-vat-rules-validated.md`.

### Task 6 — VAT Classification Data Flow Diagram
Create `/docs/diagrams/06-dataflow-vat-classification.md`:

Show the full classification decision tree for a transaction:
- Standard rated (25%)
- Zero rated (exports, intra-EU goods)
- Exempt (healthcare, education, insurance, financial services)
- Reverse charge (B2B cross-border services)
- Out of scope

Include the DK-specific exemption categories from the validated rules document. Mark any GAP items from `docs/analysis/dk-vat-rules-validated.md` with a note.

### Task 7 — Kubernetes Deployment Diagram
Create `/docs/diagrams/07-kubernetes-deployment.md`:

Show the Kubernetes deployment architecture:
- `vat-system` namespace
- **API Deployment** — 2 replicas, resource limits, liveness/readiness probes
- **PostgreSQL StatefulSet** — single instance (Phase 1), persistent volume
- **MCP Server Deployment** — 1 replica (dev/AI tooling only)
- **Services** — ClusterIP for internal, LoadBalancer/Ingress for external
- **ConfigMaps** — non-sensitive configuration
- **Secrets** — DB credentials, SKAT API key (referenced, not shown)
- **HorizontalPodAutoscaler** — on API deployment

Use `graph TD` with subgraphs for namespace and node boundaries.

### Task 8 — Jurisdiction Plugin Architecture Diagram
Create `/docs/diagrams/08-jurisdiction-plugin-architecture.md`:

Show how the plugin system works visually:
- `JurisdictionPlugin` interface at the centre
- `DkJurisdictionPlugin` as the first implementation
- Placeholder boxes for `NoJurisdictionPlugin` (Norway) and `DeJurisdictionPlugin` (Germany) — future
- `JurisdictionRegistry` that holds all registered plugins
- `TaxEngine` that uses the registry to resolve the correct plugin at runtime
- Show that adding Norway = new box only, no changes to existing boxes

This diagram is important for communicating the extensibility of the system to stakeholders.

### Task 9 — Diagrams Index
Create `/docs/diagrams/README.md`:

An index of all diagrams with:
- Diagram name and file link
- What it shows
- Who it is most useful for (developer, architect, stakeholder, operations)
- When it should be updated (e.g. "update when a new jurisdiction is added")

---

## Output Checklist
Before finishing, verify you have produced:
- [ ] `/docs/diagrams/01-c4-system-context.md`
- [ ] `/docs/diagrams/02-c4-container.md`
- [ ] `/docs/diagrams/03-entity-relationship.md`
- [ ] `/docs/diagrams/04-sequence-vat-filing.md`
- [ ] `/docs/diagrams/05-dataflow-reverse-charge.md`
- [ ] `/docs/diagrams/06-dataflow-vat-classification.md`
- [ ] `/docs/diagrams/07-kubernetes-deployment.md`
- [ ] `/docs/diagrams/08-jurisdiction-plugin-architecture.md`
- [ ] `/docs/diagrams/README.md`
- [ ] All Mermaid diagrams render without syntax errors
- [ ] Handoff Summary printed

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update the root `README.md` status table (Design Diagrams row).
- Append to `docs/agent-sessions/session-log.md`.
- Print a structured Handoff Summary.

## Constraints
- Do not write any code
- Do not modify files outside `/docs/diagrams/`
- Every diagram must be based on what actually exists — check the Java source files
- Never invent entities or relationships that don't exist in `core-domain`
- Mark any gaps or unresolved items with a visible note in the diagram
- All diagrams must render in standard Mermaid (GitHub-compatible)
- Do not use PlantUML syntax — Mermaid only
- Follow the handoff protocol from `ROLE_CONTEXT_POLICY.md`
