\# Corrections Agent — Operating Contract

\*\*File:\*\* `agents/corrections-agent/corrections-agent.md`

\*\*Status:\*\* Ready to run

\*\*Scope:\*\* Implement VAT return correction module (Phase 1.5)



---



\## Role



You are the Corrections Agent. You implement the correction and credit

note module for the VAT system. Your output enables taxpayers to correct

previously submitted VAT returns within the legally defined time window,

with full audit trail preservation per Bogføringsloven.



---



\## What You Must Read First



1\. `CLAUDE.md`

2\. `ROLE\_CONTEXT\_POLICY.md`

3\. `docs/agent-sessions/session-log.md`

4\. `docs/analysis/dk-vat-rules-validated.md` — focus on R12, R15, R16

5\. `docs/analysis/implementation-risk-register.md` — focus on correction-related gaps

6\. `docs/analysis/expert-review-answers-rubrik.md` — rubrik routing applies to corrected returns too

7\. Current `core-domain` — read the existing `Correction` record and corrections DB table

8\. Current `api` module — read existing controllers to understand patterns to follow

9\. Current `persistence` module — read existing Flyway migrations and JOOQ repos



---



\## Current Reality



\- A `Correction` domain record exists in `core-domain`

\- A `corrections` table exists in the database (Flyway migration)

\- No REST endpoint exists for corrections

\- No 3-year time window enforcement exists

\- No link between a corrected return and its original exists via REST

\- The audit ledger is append-only — corrections must add entries, never mutate existing ones



---



\## Coding Rules



\- Base package: `com.netcompany.vat.api.corrections`

\- Follow existing controller patterns exactly — thin controllers, logic in services

\- Monetary values always `long` in øre — never float or double

\- Corrections must never mutate the original return — append only

\- All correction entries must be written to the audit ledger

\- Follow the same Result/error handling pattern as existing domain code



---



\## Tasks



\### Task 1 — Domain review

Read the existing `Correction` record in `core-domain`. Verify it has:

\- `originalReturnId` — link to the return being corrected

\- `correctionReason` — free text or enum

\- `correctedAt` — timestamp

\- `correctedFields` — which rubrik fields changed and by how much



If any of these are missing, add them to the `Correction` record.



\### Task 2 — Time window enforcement

Implement a `CorrectionWindowService` in `core-domain` or `tax-engine`

that enforces the Danish 3-year ordinary correction window:



\- Ordinary corrections: within 3 years of the filing deadline

\- No corrections permitted on returns older than 3 years

&nbsp; (these require a formal amendment process outside this system)

\- The window calculation must use the filing deadline date,

&nbsp; not the submission date



\### Task 3 — Persistence

Add a Flyway migration (V004) if the corrections table needs

any new columns identified in Task 1. Add a JOOQ repository

`CorrectionRepository` following existing repo patterns.



\### Task 4 — Correction service

Implement `CorrectionService` in the `api` module:

\- `submitCorrection(returnId, correctionRequest)` — validates window,

&nbsp; validates the corrected return assembles correctly, writes to

&nbsp; corrections table, appends to audit ledger

\- `getCorrectionsForReturn(returnId)` — returns all corrections

&nbsp; linked to a given return

\- Must call `VatReturnAssembler` to verify the corrected figures

&nbsp; are internally consistent before persisting



\### Task 5 — REST endpoint

Add `CorrectionsController`:



```

POST /api/v1/returns/{id}/corrections

GET  /api/v1/returns/{id}/corrections

GET  /api/v1/corrections/{correctionId}

```



Request body for POST:

```json

{

&nbsp; "correctionReason": "string",

&nbsp; "adjustments": {

&nbsp;   "outputVatAmount": 0,

&nbsp;   "inputVatDeductibleAmount": 0

&nbsp; }

}

```



Response for POST: the assembled corrected return with

`correctionId`, `originalReturnId`, `correctedAt`, `windowExpiresAt`.



Return HTTP 422 if outside the 3-year window with a clear message.

Return HTTP 409 if the return is not in a correctable status.



\### Task 6 — Audit trail

Every correction must write two entries to the audit ledger:

1\. `CORRECTION\_SUBMITTED` — with the delta amounts

2\. `ORIGINAL\_RETURN\_LINKED` — referencing the original return ID



This preserves the full chain: original → correction → correction

of correction if applicable.



\### Task 7 — Tests

Add unit and integration tests:

\- `CorrectionWindowServiceTest` — boundary cases for 3-year window

\- `CorrectionServiceTest` — happy path, outside window, invalid return

\- `CorrectionsControllerTest` — all three endpoints

\- At least one Docker-gated IT verifying the full correction flow



\### Task 8 — Run tests

```bash

./gradlew :api:test :tax-engine:test :core-domain:test

```



All tests must pass before handoff.



---



\## Output Checklist



\- \[ ] `Correction` domain record complete with all required fields

\- \[ ] `CorrectionWindowService` with 3-year window enforcement

\- \[ ] Flyway V004 migration (if schema changes needed)

\- \[ ] `CorrectionRepository` JOOQ repo

\- \[ ] `CorrectionService` with audit ledger writes

\- \[ ] `CorrectionsController` — 3 endpoints

\- \[ ] HTTP 422 for out-of-window corrections

\- \[ ] HTTP 409 for non-correctable return status

\- \[ ] Unit tests passing

\- \[ ] Docker-gated IT passing

\- \[ ] `./gradlew :api:test` passes



---



\## Handoff Protocol



Before finishing:

1\. Update `CLAUDE.md` Last Agent Session

2\. Update `api/README.md` with new endpoints

3\. Update root `README.md` status table

4\. Append to `docs/agent-sessions/session-log.md`

5\. Update next agent's contract per `ROLE\_CONTEXT\_POLICY.md`

&nbsp;  Forward Contract Updates rule

6\. Print structured handoff summary



---



\## Constraints



\- Never mutate an existing audit ledger entry

\- Never mutate an existing VatReturn record — corrections are

&nbsp; separate records linked by `originalReturnId`

\- Do not implement the formal amendment process for returns

&nbsp; older than 3 years — return HTTP 422 with a clear message

&nbsp; explaining that formal amendment is required

\- Do not add business logic to controllers

\- Monetary values always `long` in øre

