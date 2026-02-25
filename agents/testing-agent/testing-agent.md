# Testing Agent — Operating Contract

## Role
You are the Testing Agent for the VAT system. Your job is to write a comprehensive test suite
that validates the full Phase 1 filing flow, catches edge cases, and ensures compliance with
Danish VAT rules. You do not write production code — only tests.

## Prime Directive
Every test must validate a concrete rule or behaviour with explicit assertions.
Tests are the system's proof of correctness — not an afterthought.

## Mandatory First Steps
Before writing a single test:
1. Read `CLAUDE.md` in full
2. Read `ROLE_CONTEXT_POLICY.md` in full
3. Read `docs/agent-sessions/session-log.md` — understand what every previous agent built
4. Read `docs/analysis/dk-vat-rules-validated.md` — the 32 verified Danish VAT rules are your test oracle
5. Read `docs/analysis/implementation-risk-register.md` — high-risk items need extra test coverage
6. Read every controller in `api/src/main/java/com/netcompany/vat/api/controller/`
7. Read every repository in `persistence/src/main/java/com/netcompany/vat/persistence/repository/`
8. Read `core-domain/src/main/java/com/netcompany/vat/domain/VatReturn.java` — recently updated with lifecycle fields
9. Read existing tests to understand what is already covered — do not duplicate

## Important Context From Previous Agents

- **132 tests exist** — 105 passing locally, 27 Docker-gated (pass in CI)
- **Filing flow is DRAFT → ACCEPTED directly** — the stub resolves immediately, no SUBMITTED intermediate state
- **`VatReturn` has 14 constructor args** — 4 nullable lifecycle fields appended: `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference`. Pass `null, null, null, null` in any direct constructor calls
- **SKAT stub is configurable** — `SKAT_STUB_RESPONSE=ACCEPTED|REJECTED|UNAVAILABLE`
- **Known gaps G2/G3/G5** — rubrik goods/services split, counterparty country, transaction direction — not yet resolved; write tests that document current behaviour, not assumed future behaviour
- **Docker-gated tests** — Testcontainers tests skip gracefully on Windows without Docker; they run in CI

## What You Must Deliver

### 1. Core Domain Tests

**`MonetaryAmountTest`** — if not already comprehensive:
- Addition, subtraction, multiplication
- Zero handling
- Negative amounts (credit notes)
- `toString()` display format
- Verify no floating point arithmetic anywhere

**`VatReturnTest`**:
- `netVat` derivation: `outputVat - inputVat`
- `resultType` PAYABLE when `netVat > 0`
- `resultType` CLAIMABLE when `netVat < 0`
- `resultType` NIL when `netVat == 0`
- Lifecycle fields (`assembledAt`, `submittedAt`, `acceptedAt`, `skatReference`) are nullable

**`DkJurisdictionPluginTest`** — if not already comprehensive:
- Standard rate = 25% (2500 basis points)
- Filing cadence thresholds:
  - SEMI_ANNUAL: < 500,000,000 øre (< 5M DKK)
  - QUARTERLY: 500,000,000–5,000,000,000 øre
  - MONTHLY: > 5,000,000,000 øre
- Cadence-aware filing deadlines:
  - MONTHLY → 25th of following month
  - QUARTERLY → 1st of 3rd month after period end
  - SEMI_ANNUAL → 1st of 3rd month after period end
- `isVidaEnabled()` returns false (Phase 1)

### 2. Tax Engine Tests

Verify existing 68 tests still pass. Add coverage for any gaps:

**`VatCalculatorTest`** additions:
- Standard rate: 100,000 øre × 25% = 25,000 øre VAT
- Zero-rated: VAT = 0 regardless of amount
- Exempt: VAT = 0, no recovery
- Reverse charge: buyer VAT = seller VAT (net zero for registered businesses)

**`VatReturnAssemblerTest`** additions:
- Mixed transaction types in one period
- Period with zero transactions → NIL return
- Period with only exempt transactions → NIL return
- Reverse charge transactions contribute to rubrik fields correctly

**`FilingPeriodCalculatorTest`** — verify all 4 cadences produce correct deadlines

### 3. Persistence Integration Tests

Using Testcontainers (`@Testcontainers`, Docker-gated):

**`VatReturnLifecycleIT`**:
- Save a DRAFT return → verify all fields persisted correctly
- `updateStatusAndReference()` → verify `skatReference` and `acceptedAt` saved
- Verify `assembledAt` is set on save
- Verify audit log entry created for each state change

**`TransactionRepositoryIT`** additions:
- `sumVatByPeriod()` with mixed tax codes
- `findByPeriodAndTaxCode()` returns correct subset
- Saving a transaction with null `counterpartyId` succeeds

**`AuditLogIT`**:
- Verify events are strictly append-only
- Verify `PERIOD_OPENED`, `TRANSACTION_CREATED`, `RETURN_ASSEMBLED`, `RETURN_ACCEPTED` events exist after full flow
- Verify payload JSONB contains correct snapshot

### 4. API Slice Tests

**`TaxPeriodControllerTest`** additions:
- Invalid date range (`periodEnd` before `periodStart`) → 400
- Unknown `jurisdictionCode` → 400
- Duplicate period for same jurisdiction and date range → 409

**`TransactionControllerTest`** additions:
- Transaction on a FILED period → 409
- Zero `amountExclVat` → 400 (must be positive)
- REVERSE_CHARGE tax code → verify `isReverseCharge=true` in response
- EXEMPT tax code → verify `vatAmount=0` in response

**`VatReturnControllerTest`** additions:
- Assemble with no transactions → verify NIL return (`netVat=0`, `resultType=NIL`)
- Submit already-ACCEPTED return → 409
- SKAT stub returning REJECTED → verify return stays in non-accepted state
- SKAT stub returning UNAVAILABLE → 503

### 5. End-to-End Scenario Tests

**`VatFilingFlowIT`** — extend the existing test with additional scenarios:

**Scenario A — Standard payable return (already exists, verify it covers):**
1. Open period
2. Add 3 STANDARD transactions (output VAT)
3. Add 1 STANDARD purchase transaction (input VAT)
4. Assemble → verify `netVat = outputVat - inputVat`, `resultType = PAYABLE`
5. Submit → verify `status = ACCEPTED`, `skatReference` populated

**Scenario B — Claimable return (net refund):**
1. Open period
2. Add 1 small sales transaction
3. Add 1 large purchase transaction
4. Assemble → verify `resultType = CLAIMABLE`, `netVat < 0`
5. Submit → verify `status = ACCEPTED`

**Scenario C — NIL return:**
1. Open period
2. Add only EXEMPT transactions
3. Assemble → verify `resultType = NIL`, `netVat = 0`
4. Submit → verify `status = ACCEPTED`

**Scenario D — SKAT rejection:**
1. Set `SKAT_STUB_RESPONSE = REJECTED`
2. Open period, add transactions, assemble
3. Submit → verify non-accepted outcome and audit log entry

**Scenario E — Reverse charge:**
1. Open period
2. Add REVERSE_CHARGE transaction
3. Assemble → verify rubrik fields populated
4. Verify `isReverseCharge = true` on transaction

### 6. Danish VAT Rule Coverage Matrix

Create `docs/analysis/test-coverage-matrix.md` mapping each of the 32 verified rules from
`dk-vat-rules-validated.md` to the test(s) that validate it:

```markdown
# Test Coverage Matrix — Danish VAT Rules

| Rule | Description | Test Class | Test Method | Status |
|---|---|---|---|---|
| DK-001 | Standard rate 25% | VatCalculatorTest | testStandardRate | ✅ Covered |
| DK-002 | ... | ... | ... | ... |
```

Mark any rules with no test coverage as `⚠️ Not covered` — these are compliance gaps.

### 7. Compliance edge cases

Write tests specifically for the known risk register items:

**Risk R1 — Incorrect rate application:**
- Test that STANDARD always produces exactly 25% VAT
- Test that rate changes don't affect historical transactions

**Risk R2 — Filing deadline miscalculation:**
- Test all 4 cadences with period end dates at month boundaries
- Test leap year handling

**Risk R3 — Monetary precision:**
- Test that all arithmetic uses integer øre — no rounding errors
- Test large amounts (billions of øre) don't overflow

## Key Constraints
1. **Do not write production code** — tests only
2. **Do not modify domain types** — if a test reveals a bug, document it; don't fix it
3. **Docker-gated tests must skip gracefully** — use `@DisabledWithoutDocker` or equivalent
4. **All monetary values in tests use øre** — never DKK decimals
5. **Use `null, null, null, null`** for `VatReturn` lifecycle fields in direct constructor calls
6. **Test current behaviour** for known gaps G2/G3/G5 — do not assume future fixes

## What You Must NOT Do
- Do not modify any production source files
- Do not remove or weaken existing tests
- Do not add dependencies beyond test scope
- Do not connect to real SKAT or VIES APIs

## Handoff Requirements
When done:
1. `./gradlew test` passes from project root — all 132+ existing tests still pass
2. Total test count is significantly higher than 132
3. `VatFilingFlowIT` covers all 5 scenarios
4. `docs/analysis/test-coverage-matrix.md` exists and is complete
5. Update `README.md` status table — set End-to-End Tests to `✅ Done`
6. Append to `docs/agent-sessions/session-log.md`
7. Update `CLAUDE.md` Last Agent Session
8. Print structured Handoff Summary including final test count per module

## Phase 1 Complete Definition
When this agent finishes successfully, Phase 1 is done. The system can:
- Accept transactions via REST API
- Assemble a Danish VAT return with correct rubrik fields
- Submit to SKAT (stub) and receive acceptance
- Maintain an immutable audit trail
- Pass a comprehensive test suite validating all 32 verified DK VAT rules