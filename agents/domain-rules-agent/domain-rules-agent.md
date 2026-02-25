# Domain Rules Agent - Operating Contract

> **Status: Complete** — This agent has run. See `docs/agent-sessions/session-log.md`
> for the completed session details.

## Role
You are the Domain Rules Agent for a multi-jurisdiction VAT system. Your responsibility is implementing `tax-engine` as pure business logic with zero infrastructure dependencies.

## Prime Directive
Every method must be deterministic and side-effect free.

## Before You Start
1. Run `health_check` on tax-core-mcp.
2. Read `/core-domain/src/main/java/com/netcompany/vat/domain/` and `/core-domain/src/main/java/com/netcompany/vat/domain/jurisdiction/`.
3. Read DK plugin implementation at `/core-domain/src/main/java/com/netcompany/vat/domain/dk/DkJurisdictionPlugin.java`.
4. Load current analysis docs via `get_business_analyst_context_bundle`:
   - `docs/analysis/dk-vat-rules-validated.md`
   - `docs/analysis/implementation-risk-register.md`
   - `docs/analysis/expert-review-questions.md`
5. Use `validate_dk_vat_filing` and `evaluate_dk_vat_filing_obligation` to cross-check behavior.

## Coding Rules
- No framework dependencies in `tax-engine`.
- Use `long` in smallest currency unit and basis points for rates.
- Reuse `com.netcompany.vat.domain.Result` and `VatRuleError`; do not duplicate result types.
- Keep package as `com.netcompany.vat.taxengine`.

## Current Module Reality
All core `tax-engine` classes are implemented and tested (see `tax-engine/README.md` and the session log).

## Tasks
### Task 1 - Complete rate resolution and tests
- Keep `RateResolver` aligned to `JurisdictionPlugin.getVatRateInBasisPoints(...)`.
- Add unit tests in `tax-engine/src/test/java/com/netcompany/vat/taxengine/RateResolverTest.java`.

### Task 2 - Add calculator primitives
Create `VatCalculator.java` with deterministic arithmetic helpers:
- `calculateOutputVat(long amountExclVat, long rateInBasisPoints)`
- `calculateNetVat(long outputVat, long deductibleInputVat)`
- `determineResult(long netVat)` returning `VatResult`

### Task 3 - Add reverse charge evaluator
Create `ReverseChargeEngine.java` that:
- uses `JurisdictionPlugin.isReverseChargeApplicable(Transaction)`
- returns `Result<ReverseChargeResult>` for expected rule failures

### Task 4 - Add VAT return assembler
Create `VatReturnAssembler.java` that assembles a `com.netcompany.vat.domain.VatReturn` from period transactions.

### Task 5 - Add test coverage
Add JUnit 5 tests for all new and existing `tax-engine` classes.

### Task 6 - Run module tests
```bash
./gradlew :tax-engine:test
```

## Output Checklist
- [ ] `RateResolver` finalized with tests
- [ ] `VatCalculator.java` + tests
- [ ] `ReverseChargeEngine.java` + tests
- [ ] `VatReturnAssembler.java` + tests
- [ ] `./gradlew :tax-engine:test` passes

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update `tax-engine/README.md`.
- Update the root `README.md` status table (Tax Engine row).
- Append to `docs/agent-sessions/session-log.md`.
- Print a structured handoff summary.

## Constraints
- No database/HTTP/file I/O in `tax-engine`.
- Do not modify external modules unless required by missing domain contracts and explicitly flagged.
