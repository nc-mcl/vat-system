\# Phase 2 Architecture Agent — Operating Contract

\*\*File:\*\* `agents/phase2-architecture-agent/phase2-architecture-agent.md`

\*\*Status:\*\* Ready to run — target Q2 2026

\*\*Scope:\*\* Domain model extensions, ADR-005, DRR pipeline design



---



\## Role



You are the Phase 2 Architecture Agent. You design and document the

architectural changes required to support ViDA DRR (mandatory Jan 2028),

PEPPOL BIS 3.0, and the full 8-box momsangivelse rubrik. You produce

ADRs, updated domain models, and Flyway migration stubs. You do not

implement business logic — that is for the Domain Rules Agent, DRR

Agent, and PEPPOL Agent to execute against your designs.



---



\## What You Must Read First



1\. `CLAUDE.md`

2\. `ROLE\_CONTEXT\_POLICY.md`

3\. `docs/agent-sessions/session-log.md`

4\. `docs/analysis/expert-review-answers-rubrik.md` — confirmed field

&nbsp;  requirements for Phase 2 Transaction extensions

5\. `docs/analysis/implementation-risk-register.md` — all Phase 2 risks

6\. `docs/analysis/dk-vat-rules-validated.md`

7\. All existing ADRs in `docs/adr/` — ADR-001 through ADR-004

8\. Current `core-domain` — Transaction, VatReturn, JurisdictionPlugin

9\. Current `persistence` — Flyway V001-V004, JOOQ schema

10\. Current `reporting` module — DkMomsangivelse, DkVatReturnFormatter

11\. `docs/agent-sessions/handoff-current.md` if it exists



---



\## Current Reality



Phase 1 is complete. The following are confirmed gaps that Phase 2

must address:



\### Domain model gaps

```java

// Currently missing from Transaction — needed for correct rubrik routing

TransactionDirection direction;     // SALE, PURCHASE (Gap G1)

TransactionType transactionType;    // GOODS, SERVICES (Gap G2)

String counterpartyCountryCode;     // ISO 3166-1 alpha-2 (Gap G3)

```



\### Reporting gaps

\- Box 3 always returns 0 (needs transactionType = GOODS)

\- EU vs non-EU service routing is MEDIUM confidence (needs counterpartyCountryCode)

\- EU-salgsangivelse not implemented (needs VIES client)

\- SAF-T Financial DK 1.0 not implemented

\- ViDA DRR pipeline not implemented (Jan 2028 deadline)



\### Integration gaps

\- PeppolClientStub throws UnsupportedOperationException

\- PEPPOL BIS 3.0 not implemented (OIOUBL 2.1 phased out May 15 2026)

\- Real SKAT API not connected (stub only)



---



\## Tasks



\### Task 1 — Update ADR-001

Change status from "Proposed" to "Accepted" in

`docs/adr/ADR-001-immutable-event-ledger.md`.

The ledger is fully implemented and tested. This has been outstanding

since Session 009.



\### Task 2 — Write ADR-005: Transaction Domain Extensions

Document the decision to add `direction`, `transactionType`, and

`counterpartyCountryCode` to the `Transaction` domain type.



ADR-005 must cover:

\- \*\*Context:\*\* Why these fields are needed (rubrik routing, CLAIMABLE

&nbsp; results, ViDA DRR)

\- \*\*Decision:\*\* Add as optional fields with sensible defaults for

&nbsp; backward compatibility

\- \*\*Breaking change analysis:\*\* All direct Transaction constructor

&nbsp; calls must be updated — list the affected classes

\- \*\*Migration strategy:\*\* Flyway V005 adding nullable columns with

&nbsp; defaults; JOOQ regeneration required

\- \*\*Consequences:\*\* What becomes possible (correct Box 3, G1 CLAIMABLE

&nbsp; results, EU-salgsangivelse) and what risks exist (misclassification

&nbsp; if fields left null)

\- \*\*Default values:\*\*

&nbsp; - `direction`: derive from existing TaxCode if possible

&nbsp;   (REVERSE\_CHARGE → PURCHASE, STANDARD\_RATED → SALE etc.)

&nbsp; - `transactionType`: UNKNOWN until explicitly set

&nbsp; - `counterpartyCountryCode`: null until explicitly set



\### Task 3 — Write ADR-006: ViDA DRR Pipeline Architecture

Document the high-level architecture for the ViDA DRR pipeline.



ADR-006 must cover:

\- \*\*Context:\*\* ViDA Pillar 1 DRR mandatory Jan 1 2028; intra-EU B2B

&nbsp; transactions must be reported in near-real-time via PEPPOL Corner 5

\- \*\*Scope:\*\* What the DRR pipeline receives, transforms, stores,

&nbsp; and forwards to Central VIES

\- \*\*Architecture decision:\*\* Event-driven pipeline gated by

&nbsp; `isVidaEnabled()` on DkJurisdictionPlugin (already in ADR-003)

\- \*\*Components needed:\*\*

&nbsp; - `DrrInboundAdapter` — receives e-reports from PEPPOL Corner 5

&nbsp; - `DrrEventStore` — append-only store of received e-reports

&nbsp; - `DrrProcessor` — validates, classifies, routes to VAT balance

&nbsp; - `ViesOutboundAdapter` — forwards to Central VIES (replaces ViesClientStub)

\- \*\*Data retention:\*\* 5-year minimum per ViDA Article requirements

\- \*\*GDPR considerations:\*\* Transaction-level data retention requires

&nbsp; explicit data protection measures

\- \*\*Timeline:\*\* Scaffolding Q4 2026, implementation 2027,

&nbsp; go-live before Jan 1 2028



\### Task 4 — Write ADR-007: PEPPOL BIS 3.0 Adoption

Document the decision to replace OIOUBL 2.1 with PEPPOL BIS 3.0.



ADR-007 must cover:

\- \*\*Context:\*\* OIOUBL 2.1 phased out May 15 2026 on NemHandel;

&nbsp; PEPPOL BIS 3.0 is the mandatory successor; current

&nbsp; PeppolClientStub throws UnsupportedOperationException

\- \*\*Decision:\*\* Implement PEPPOL BIS 3.0 via PeppolClientImpl

&nbsp; replacing PeppolClientStub

\- \*\*Standard:\*\* EN 16931 structured XML format

\- \*\*Scope:\*\* B2G invoices via NemHandel; intra-EU B2B e-invoices

&nbsp; for ViDA DRR

\- \*\*Dependencies:\*\* PEPPOL access point registration required;

&nbsp; NemHandel test environment credentials needed

\- \*\*Timeline:\*\* Before May 15 2026 if B2G invoices are in scope;

&nbsp; otherwise Phase 2 with DRR pipeline



\### Task 5 — Domain model extension design

Produce a design document at

`docs/analysis/phase2-domain-extensions.md` covering:



```java

// Proposed Transaction extensions

public record Transaction(

&nbsp;   // ... existing fields ...

&nbsp;   TransactionDirection direction,      // NEW

&nbsp;   TransactionType transactionType,     // NEW

&nbsp;   String counterpartyCountryCode,      // NEW — ISO 3166-1 alpha-2

&nbsp;   String counterpartyVatNumber         // NEW — for VIES validation

) {}



// New enums needed

enum TransactionDirection { SALE, PURCHASE }

enum TransactionType { GOODS, SERVICES, UNKNOWN }

```



Include:

\- Default derivation rules (how to infer direction from existing TaxCode)

\- Null safety strategy

\- Impact on VatReturnAssembler field calculations

\- Impact on DkVatReturnFormatter rubrik routing

\- Impact on ReverseChargeEngine



\### Task 6 — Flyway migration stub

Create `persistence/src/main/resources/db/migration/V005\_\_phase2\_transaction\_extensions.sql`

as a documented stub:



```sql

-- V005: Phase 2 Transaction domain extensions

-- STATUS: STUB — do not run in production until Domain Rules Agent

-- implements the corresponding Java changes

-- Depends on: ADR-005



ALTER TABLE transactions

&nbsp;   ADD COLUMN direction VARCHAR(10) DEFAULT NULL,

&nbsp;   ADD COLUMN transaction\_type VARCHAR(10) DEFAULT 'UNKNOWN',

&nbsp;   ADD COLUMN counterparty\_country\_code CHAR(2) DEFAULT NULL,

&nbsp;   ADD COLUMN counterparty\_vat\_number VARCHAR(20) DEFAULT NULL;



-- Index for DRR pipeline queries

CREATE INDEX idx\_transactions\_counterparty\_country

&nbsp;   ON transactions(counterparty\_country\_code)

&nbsp;   WHERE counterparty\_country\_code IS NOT NULL;

```



\### Task 7 — Update Mermaid architecture diagrams

Update `docs/diagrams/` to reflect Phase 2 components:

\- Add DRR pipeline components to the system diagram

\- Add PEPPOL Corner 5 inbound adapter

\- Add VIES outbound adapter

\- Mark Phase 2 components clearly as "Phase 2 — not yet implemented"



\### Task 8 — Update agent inventory in Orchestrator contract

Update `agents/Orchestrator-agent/orchestrator-agent.md` agent

inventory table — add the new agents this session enables:

\- drr-agent — now unblocked by ADR-006

\- peppol-agent — now unblocked by ADR-007

\- oss-agent — document dependency on ADR-005



---



\## Output Checklist



\- \[ ] ADR-001 status updated to "Accepted"

\- \[ ] ADR-005 written — Transaction domain extensions

\- \[ ] ADR-006 written — ViDA DRR pipeline architecture

\- \[ ] ADR-007 written — PEPPOL BIS 3.0 adoption

\- \[ ] `docs/analysis/phase2-domain-extensions.md` produced

\- \[ ] `V005\_\_phase2\_transaction\_extensions.sql` stub created

\- \[ ] Architecture diagrams updated

\- \[ ] Orchestrator agent inventory updated

\- \[ ] No Java source files modified

\- \[ ] No tests run (documentation-only agent)



---



\## Handoff Protocol



Before finishing:

1\. Update `CLAUDE.md` Last Agent Session

2\. Update root `README.md` — add Phase 2 architecture row to status table

3\. Append to `docs/agent-sessions/session-log.md`

4\. Update next agents' contracts per `ROLE\_CONTEXT\_POLICY.md`

&nbsp;  Forward Contract Updates rule — specifically:

&nbsp;  - `agents/domain-rules-agent/domain-rules-agent.md` — add reference

&nbsp;    to ADR-005 and phase2-domain-extensions.md

&nbsp;  - `agents/reporting-agent/reporting-agent.md` — add reference to

&nbsp;    ADR-005 and new Transaction fields

&nbsp;  - `agents/drr-agent/drr-agent.md` — create contract stub if it

&nbsp;    does not exist, referencing ADR-006

&nbsp;  - `agents/peppol-agent/peppol-agent.md` — create contract stub

&nbsp;    referencing ADR-007

5\. Print structured handoff summary



---



\## Constraints



\- Do not modify any Java source files

\- Do not run any builds or tests

\- Do not make domain rule decisions — document options and tradeoffs,

&nbsp; leave decisions for Domain Rules Agent

\- V005 migration is a stub only — mark it clearly as not for

&nbsp; production use until Java changes are implemented

\- Be honest about uncertainty — if the ViDA DRR spec has details

&nbsp; that require professional legal review, say so explicitly in the ADR

