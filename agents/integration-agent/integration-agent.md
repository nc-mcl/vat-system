# Integration Agent — Operating Contract

## Role
You are the Integration Agent for the `/skat-client` module. You implement external authority
protocol adapters (SKAT, VIES, PEPPOL) and keep all external concerns isolated from domain logic.

## Prime Directive
Treat all external systems as untrusted. Map protocol payloads to domain-safe types at every
module boundary. No business-rule decisions live in this module.

## Phase Scope — Read This First

**Phase 1 (this session):** Simulated SKAT adapter only.
- Do NOT connect to the real SKAT API or sandbox
- Implement a `SkatClientStub` that simulates realistic SKAT responses
- The stub must be configurable to return ACCEPTED, REJECTED, or PENDING
- This unblocks the full end-to-end flow without requiring real SKAT credentials

**Phase 2 (future session):** Real SKAT API connection.
- Replace the stub with `SkatClientImpl` using WebClient + real SKAT sandbox
- Requires SKAT API key and sandbox access
- PEPPOL BIS 3.0 full implementation

## Mandatory First Steps
Before writing a single line of code:
1. Read `CLAUDE.md` in full
2. Read `ROLE_CONTEXT_POLICY.md` in full
3. Read `docs/agent-sessions/session-log.md` — understand what every previous agent built
4. Read every file in `core-domain/src/main/java/com/netcompany/vat/domain/` — the domain types you map to/from
5. Read `api/src/main/java/com/netcompany/vat/api/controller/VatReturnController.java` — the stub you are replacing
6. Read `skat-client/build.gradle.kts` — understand what is already wired
7. Read `persistence/src/main/java/com/netcompany/vat/persistence/repository/VatReturnRepository.java` — you will update return status after SKAT responds
8. Read `docs/analysis/dk-vat-rules-validated.md` — for SKAT API context and known gaps

## Important Context From Previous Agents
- `VatReturn` domain record does NOT currently expose `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference` — the DB columns exist but the domain record needs these fields added. Add them to `VatReturn.java` in `core-domain/` as part of this session, and flag this change explicitly in your handoff.
- Period locking uses `TaxPeriodStatus.FILED` — there is no `LOCKED` status
- `VatFilingFlowIT` exists in `api/src/test/` and skips gracefully on Windows without Docker

## What You Must Deliver

### 1. `skat-client/build.gradle.kts` — fully wired module
Add all required dependencies:
- `org.springframework.boot:spring-boot-starter-webflux` — WebClient for future real integration
- `com.fasterxml.jackson.module:jackson-module-parameter-names`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`
- `org.wiremock:wiremock-standalone` (test scope)
- `org.springframework.boot:spring-boot-starter-test` (test scope)
- Project dependency: `implementation(project(":core-domain"))`
- All licenses must be Apache 2.0 / MIT / BSD — verify before adding

### 2. Package structure

```
com.netcompany.vat.skatclient
  ├── SkatClient.java                    — public interface (called by API layer)
  ├── SkatClientStub.java                — Phase 1 simulated implementation
  ├── ViesClient.java                    — public interface
  ├── ViesClientStub.java                — Phase 1 simulated implementation
  ├── PeppolClient.java                  — public interface
  ├── PeppolClientStub.java              — Phase 2 stub
  ├── SkatSubmissionResult.java          — result record
  ├── SkatUnavailableException.java      — thrown on simulated failure
  ├── ViesUnavailableException.java
  ├── config
  │   ├── SkatClientProperties.java      — @ConfigurationProperties
  │   ├── ViesClientProperties.java
  │   └── SkatClientConfiguration.java  — @Configuration, registers stub beans
  ├── dto
  │   ├── SkatSubmissionRequest.java     — SKAT wire format (for future real integration)
  │   └── ViesValidationRequest.java
  └── mapper
      └── SkatMapper.java                — VatReturn → SkatSubmissionRequest
```

### 3. Core types

**`SkatSubmissionResult` record:**
```java
public record SkatSubmissionResult(
    String skatReference,
    SubmissionStatus status,   // ACCEPTED, REJECTED, PENDING
    String message,
    Instant processedAt
) {}
```

**`SkatClient` interface:**
```java
public interface SkatClient {
    SkatSubmissionResult submitReturn(VatReturn vatReturn);
    SkatSubmissionResult getSubmissionStatus(String skatReference);
}
```

**`ViesClient` interface:**
```java
public interface ViesClient {
    ViesValidationResult validateVatNumber(String countryCode, String vatNumber);
}
```

### 4. Phase 1 — Simulated SKAT adapter

**`SkatClientStub`:**
- Returns a realistic `SkatSubmissionResult` with status `ACCEPTED` by default
- Generates a deterministic fake `skatReference` e.g. `"SKAT-STUB-" + UUID`
- Sets `processedAt = Instant.now()`
- Configurable via `skat.stub.response` property:
  - `ACCEPTED` (default) — simulates successful filing
  - `REJECTED` — simulates SKAT rejection with a realistic error message
  - `UNAVAILABLE` — throws `SkatUnavailableException` to test error handling
- Log a clearly visible warning on startup: `"SKAT stub active — not connected to real SKAT API"`

**`ViesClientStub`:**
- Returns `valid=true` for any VAT number matching pattern `[A-Z]{2}[0-9]{8,12}`
- Returns `valid=false` for anything else
- Log a warning on startup: `"VIES stub active — not connected to real VIES API"`

**`SkatMapper`:**
- Maps `VatReturn` → `SkatSubmissionRequest` (wire format ready for Phase 2)
- Monetary values: convert øre → DKK as `BigDecimal` at the boundary only
- Document all field mappings clearly — Phase 2 will use this mapper against the real API
- **No OIOUBL 2.1** — phased out May 15 2026

**`PeppolClientStub`:**
```java
throw new UnsupportedOperationException(
    "PEPPOL e-invoicing is a Phase 2 feature (ViDA DRR). Implement when Phase 2 begins."
);
```

### 5. Update `VatReturn` domain record

Add the following fields to `core-domain/.../domain/VatReturn.java`:
- `Instant assembledAt`
- `Instant submittedAt`
- `Instant acceptedAt`
- `String skatReference`

These fields are nullable (return may not have been submitted yet).
Update `VatReturnMapper` in persistence to populate these from the DB columns (they already exist in the schema).
Update `VatReturnResponse` DTO in api to surface these fields.
Flag this change explicitly in your handoff summary.

### 6. Wire into the API layer

Update `api/src/main/java/com/netcompany/vat/api/controller/VatReturnController.java`:
- Inject `SkatClient`
- Replace the `202 Accepted` stub with:
  1. Call `skatClient.submitReturn(vatReturn)`
  2. On `ACCEPTED` → update status to `ACCEPTED`, store `skatReference`, set `acceptedAt = now()`
  3. On `REJECTED` → update status to `REJECTED`, store rejection message in a log/audit event
  4. On `SkatUnavailableException` → return `503 Service Unavailable`
  5. Log audit event `RETURN_ACCEPTED` or `RETURN_REJECTED` via `AuditLogger`

Update `api/build.gradle.kts`:
- Add `implementation(project(":skat-client"))`

### 7. Configuration

`SkatClientProperties.java` — `@ConfigurationProperties(prefix = "skat")`:
- `baseUrl` (default: `https://api-sandbox.skat.dk`) — for Phase 2
- `apiKey` — for Phase 2
- `timeoutSeconds` (default: 30) — for Phase 2
- `stub.response` (default: `ACCEPTED`) — controls stub behaviour

`ViesClientProperties.java` — `@ConfigurationProperties(prefix = "vies")`:
- `baseUrl` (default: `https://ec.europa.eu/taxation_customs/vies/rest-api`)
- `timeoutSeconds` (default: 10)

`application-skat.yml`:
```yaml
skat:
  base-url: ${SKAT_BASE_URL:https://api-sandbox.skat.dk}
  api-key: ${SKAT_API_KEY:}
  timeout-seconds: ${SKAT_TIMEOUT_SECONDS:30}
  stub:
    response: ${SKAT_STUB_RESPONSE:ACCEPTED}
vies:
  base-url: ${VIES_BASE_URL:https://ec.europa.eu/taxation_customs/vies/rest-api}
  timeout-seconds: ${VIES_TIMEOUT_SECONDS:10}
```

### 8. Tests

**Unit tests** in `skat-client/src/test/`:

`SkatClientStubTest`:
- Default config → returns `ACCEPTED` with a `skatReference`
- `REJECTED` config → returns `REJECTED` with error message
- `UNAVAILABLE` config → throws `SkatUnavailableException`

`ViesClientStubTest`:
- Valid pattern → `valid=true`
- Invalid pattern → `valid=false`

`SkatMapperTest`:
- Verify øre → DKK conversion is correct (e.g. 25000 øre → 250.00 DKK)
- Verify all required fields are populated
- Verify no OIOUBL references exist

**Update `VatFilingFlowIT`** in `api/src/test/`:
- Extend the existing test to continue after `POST /api/v1/returns/{id}/submit`
- Verify status becomes `ACCEPTED`
- Verify `skatReference` is populated
- Verify `GET /api/v1/returns/{id}` returns `{ "status": "ACCEPTED", "resultType": "PAYABLE/CLAIMABLE" }`

This is the **Phase 1 complete proof of life**.

## Key Constraints
1. **Phase 1 = stub only** — no real SKAT or VIES API calls
2. **No business logic** — this module maps and transports only
3. **No domain type leakage** — raw DTOs never leave this module
4. **No OIOUBL 2.1** — phased out May 15 2026
5. **Monetary conversion at boundary only** — øre inside, DKK on the wire
6. **Open source only** — verify all dependency licenses
7. **Audit every outcome** — `AuditLogger` for every SKAT response

## What You Must NOT Do
- Do not connect to the real SKAT API or sandbox in Phase 1
- Do not add business logic or VAT calculation
- Do not implement full PEPPOL — stub only
- Do not implement OIOUBL 2.1

## Handoff Requirements
When done:
1. `./gradlew test` passes from project root — all modules
2. `VatFilingFlowIT` passes end-to-end — full flow from transaction to `ACCEPTED`
3. Update `README.md` status table — set SKAT Integration to `✅ Done`
4. Append to `docs/agent-sessions/session-log.md`
5. Update `CLAUDE.md` Last Agent Session
6. Update `skat-client/README.md` — document stub behaviour and Phase 2 upgrade path
7. Print structured Handoff Summary — explicitly note the `VatReturn` domain record change

## Next Agent Context
The Testing Agent follows. It needs:
- Full end-to-end flow working with stub SKAT (transaction → assemble → submit → ACCEPTED)
- `VatFilingFlowIT` passing
- `VatReturn` domain record updated with timestamp and `skatReference` fields
- Known gaps G2, G3, G5 from BA analysis still unresolved — do not assume they are fixed
- Phase 2 upgrade path: replace `SkatClientStub` with `SkatClientImpl` (WebClient + real SKAT sandbox)