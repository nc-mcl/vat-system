# API Agent — Operating Contract

> **Status: Complete** — This agent has run. See `docs/agent-sessions/session-log.md`
> Session 008 for what was built, and `api/README.md` for module documentation.
>
> **Deviations from plan (important for next agents):**
> - Period locking uses `TaxPeriodStatus.FILED` — no `LOCKED` variant exists in the domain enum
> - `VatReturn` domain record does not expose `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference` — DB columns exist but domain record needs updating before these surface in responses
> - `VatFilingFlowIT` skips automatically on Windows without Docker; runs fully in CI

---

## Your Role

You are the API Agent for the VAT system. Your job is to implement the Spring Boot REST API
that exposes the tax engine and persistence layer as a usable HTTP service.

## Mandatory First Steps
Before writing a single line of code:
1. Read `CLAUDE.md` in full
2. Read `ROLE_CONTEXT_POLICY.md` in full
3. Read `docs/agent-sessions/session-log.md` — understand what every previous agent built
4. Read every file in `persistence/src/main/java/com/netcompany/vat/persistence/repository/` — these are the beans you will inject
5. Read every file in `core-domain/src/main/java/com/netcompany/vat/domain/` — these are the domain types you work with
6. Read `tax-engine/src/main/java/com/netcompany/vat/taxengine/TaxEngine.java` — the facade you will call
7. Read `persistence/src/main/resources/application-persistence.yml` — datasource config already defined
8. Read `api/build.gradle.kts` — understand what is already wired
9. Read `api/Dockerfile` — already exists, do not modify it

## What You Must Deliver

### 1. `api/build.gradle.kts` — fully wired module
Add all required dependencies:
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-actuator`
- `net.logstash.logback:logstash-logback-encoder:7.4`
- `com.fasterxml.jackson.module:jackson-module-parameter-names`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`
- Project dependencies: `implementation(project(":core-domain"))`, `implementation(project(":tax-engine"))`, `implementation(project(":persistence"))`
- Test: `org.springframework.boot:spring-boot-starter-test`, `org.testcontainers:postgresql`
- All licenses must be Apache 2.0 / MIT / BSD — verify before adding

Apply the Spring Boot plugin so `bootJar` works — this is required for the existing `api/Dockerfile`.

### 2. Application entry point

`ApiApplication.java` in `com.netcompany.vat.api`:
- Standard `@SpringBootApplication`
- Component scan must include `com.netcompany.vat.persistence` to pick up persistence beans

`application.yml` in `api/src/main/resources/`:
```yaml
spring:
  profiles:
    include: persistence
  application:
    name: vat-api
server:
  port: ${PORT:8080}
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

### 3. REST Controllers

Base path: `/api/v1`

#### `TaxPeriodController` — `/api/v1/periods`

**POST `/api/v1/periods`** — open a new filing period
- Request: `OpenPeriodRequest(jurisdictionCode, periodStart, periodEnd, filingCadence)`
- Response: `TaxPeriodResponse`
- Logic: validate dates, call `DkJurisdictionPlugin.calculateFilingDeadline()`, call `TaxPeriodRepository.save(period, filingDeadline)`, log audit event `PERIOD_OPENED`
- Returns `201 Created`

**GET `/api/v1/periods/{id}`** — `404` if not found

**GET `/api/v1/periods?jurisdictionCode=DK`** — list periods for a jurisdiction

#### `TransactionController` — `/api/v1/transactions`

**POST `/api/v1/transactions`**
- Request: `CreateTransactionRequest(periodId, taxCode, amountExclVat, transactionDate, description, counterpartyId?)`
- `amountExclVat` is in **øre** (long)
- Logic: look up period (`404`/`409` if not OPEN), resolve classification via TaxEngine, calculate VAT, save, log `TRANSACTION_CREATED`
- Returns `201 Created`

**GET `/api/v1/transactions?periodId={id}`**

#### `VatReturnController` — `/api/v1/returns`

**POST `/api/v1/returns/assemble`**
- Logic: look up period, check no return exists (`409`), fetch transactions, call `VatReturnAssembler.assemble()`, save return, update period status to `FILED`, log `RETURN_ASSEMBLED`
- Returns `201 Created`

**POST `/api/v1/returns/{id}/submit`**
- Updates status to `SUBMITTED`, logs `RETURN_SUBMITTED`
- **SKAT integration NOT implemented** — returns `202 Accepted` (Integration Agent replaces this)

**GET `/api/v1/returns/{id}`** — `404` if not found

**GET `/api/v1/returns?periodId={id}`**

#### `CounterpartyController` — `/api/v1/counterparties`

**POST `/api/v1/counterparties`** — `201 Created`

**GET `/api/v1/counterparties/{id}`** — `404` if not found

### 4. DTOs

All in `com.netcompany.vat.api.dto`, as Java records:
- Monetary amounts: `long` øre — never `double` or `BigDecimal`
- UUIDs: `String` on the wire
- Dates: ISO 8601 strings
- Enums: `name()` string

**`TaxPeriodResponse`**: `id`, `jurisdictionCode`, `periodStart`, `periodEnd`, `filingCadence`, `filingDeadline`, `status`

**`TransactionResponse`**: `id`, `periodId`, `taxCode`, `taxClassification`, `amountExclVat`, `vatAmount`, `rateBasisPoints`, `transactionDate`, `description`, `isReverseCharge`, `counterpartyId`

**`VatReturnResponse`**: `id`, `periodId`, `jurisdictionCode`, `outputVat`, `inputVat`, `netVat`, `resultType`, `rubrikA`, `rubrikB`, `status`, `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference`

**`CounterpartyResponse`**: `id`, `name`, `vatNumber`, `countryCode`

### 5. Validation
Jakarta Bean Validation on all request DTOs: `@NotNull`, `@NotBlank`, `@Positive` on amounts, `@Pattern(regexp = "[A-Z]{2}")` on `countryCode`.

### 6. Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception | HTTP Status | Response |
|---|---|---|
| `EntityNotFoundException` | 404 | `{ "error": "NOT_FOUND", "message": "..." }` |
| `InvalidPeriodStateException` | 409 | `{ "error": "CONFLICT", "message": "..." }` |
| `MethodArgumentNotValidException` | 400 | `{ "error": "VALIDATION_FAILED", "violations": [...] }` |
| `Exception` | 500 | `{ "error": "INTERNAL_ERROR", "message": "..." }` |

### 7. Tests

Unit tests (Mockito, no Spring context):
- `TaxPeriodControllerTest` (4 tests)
- `TransactionControllerTest` (4 tests)
- `VatReturnControllerTest` (6 tests)

Integration test (Testcontainers, skips on Windows without Docker):
- `VatFilingFlowIT` — full Phase 1 flow: open period → add transactions → assemble return → submit → verify SUBMITTED

### 8. OpenAPI
Swagger UI at `/swagger-ui.html`. Add `@Operation` and `@Tag` to all controllers.

## Key Constraints
1. No domain logic in API layer — controllers orchestrate only
2. No monetary floats — `long` øre everywhere
3. Audit every state change via `AuditLogger`
4. `TaxPeriodRepository.save()` requires `(TaxPeriod, LocalDate filingDeadline)`
5. Open source only
6. Do not modify `api/Dockerfile`

## What You Must NOT Do
- Do not modify `core-domain/`, `tax-engine/`, or `persistence/`
- Do not implement SKAT API calls — stub with `202 Accepted`
- Do not use `double`, `float`, or `BigDecimal` for monetary values
- Do not add JPA/Hibernate

## Handoff Requirements
1. `./gradlew test` passes from project root
2. `VatFilingFlowIT` passes (or skips gracefully on Windows)
3. Update `README.md` status table — set REST API to `✅ Done`
4. Append to `docs/agent-sessions/session-log.md`
5. Update `CLAUDE.md` Last Agent Session
6. Update `api/README.md`
7. Print structured Handoff Summary

## Next Agent Context
The Integration Agent follows. It needs:
- `POST /api/v1/returns/{id}/submit` exists and returns `202 Accepted` — replace with real SKAT call
- `skat-client` module is where SKAT integration code lives
- SKAT sandbox: `https://api-sandbox.skat.dk`
- OIOUBL 2.1 phased out May 15 2026 — use PEPPOL BIS 3.0 only
- `VatReturn` domain record needs `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference` fields added before timestamps surface in API responses