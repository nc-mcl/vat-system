# Reporting Agent - Operating Contract

> **Status: Complete** -- This agent has run. See `docs/agent-sessions/session-log.md`
> Session 013 for what was built.

## Role
You are the Reporting Agent for VAT output generation. In Phase 1, reporting lives inside the `/api` module.

## Prime Directive
Reporting transforms existing persisted/domain data into authority-facing payloads and must not mutate business state.

## Before You Start
1. Run `health_check` on tax-core-mcp.
2. Read current `core-domain` records (`VatReturn`, `Transaction`, `TaxPeriod`, `JurisdictionCode`).
3. Read current `api` module state (controllers exist; reporting endpoints do not).
4. Load analysis docs via `get_business_analyst_context_bundle`:
   - `docs/analysis/dk-vat-rules-validated.md`
   - `docs/analysis/implementation-risk-register.md`
5. Read `docs/analysis/expert-review-answers-rubrik.md` — this is the
   definitive rubrik routing reference. The summary table at the top
   defines all field mappings. G3 non-EU service net value is MEDIUM
   confidence — add explicit code comments where implemented.

## Current Module Reality
- `api` has controllers for periods/transactions/returns, but no reporting-specific services or endpoints yet.
- `core-domain` contains `VatReturn` (not `TaxReturn`) and no `ReportFormatter` interface.

## Coding Rules
- Base package: `com.netcompany.vat.api.reporting`.
- Keep formatters deterministic and testable.
- Keep controller logic thin.

## Tasks
### Task 1 - Formatter foundation
Create a DK return formatter around `VatReturn` and DK `jurisdictionFields` rubrik values.

### Task 2 - Reporting service
Create service methods to generate payload bytes from persisted returns.

### Task 3 - API endpoints
Add controller endpoints for:
- draft generation trigger
- payload retrieval
- submission handoff trigger

### Task 4 - Phase 2 stubs
Add explicit stubs for SAF-T and ViDA DRR with clear exception messages.

### Task 5 - Tests
Add unit and slice tests for formatter/services/controllers.

### Task 6 - Run tests
```bash
./gradlew :api:test
```

## Output Checklist
- [ ] DK formatter built on `VatReturn`
- [ ] Reporting service and facade implemented
- [ ] Reporting controller endpoints and tests
- [ ] SAF-T and DRR stubs with explicit messages
- [ ] `./gradlew :api:test` passes

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update `api/README.md`.
- Update the root `README.md` status table if you completed a tracked layer.
- Append to `docs/agent-sessions/session-log.md`.
- Print a structured handoff summary.

## Constraints
- Do not reference non-existent `TaxReturn`, `Invoice`, or `ReportFormatter` contracts.
- No business-rule recalculation in controllers.
