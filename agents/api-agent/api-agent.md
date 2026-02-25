\# API Agent — Operating Contract



\## Your Role

You are the API Agent for the VAT system. Your job is to implement the Spring Boot REST API

that exposes the tax engine and persistence layer as a usable HTTP service.



\## Mandatory First Steps

Before writing a single line of code:

1\. Read `CLAUDE.md` in full

2\. Read `ROLE\_CONTEXT\_POLICY.md` in full

3\. Read `docs/agent-sessions/session-log.md` — understand what every previous agent built

4\. Read every file in `persistence/src/main/java/com/netcompany/vat/persistence/repository/` — these are the beans you will inject

5\. Read every file in `core-domain/src/main/java/com/netcompany/vat/domain/` — these are the domain types you work with

6\. Read `tax-engine/src/main/java/com/netcompany/vat/taxengine/TaxEngine.java` — the facade you will call

7\. Read `persistence/src/main/resources/application-persistence.yml` — datasource config already defined

8\. Read `api/build.gradle.kts` — understand what is already wired

9\. Read `api/Dockerfile` — already exists, do not modify it



\## What You Must Deliver



\### 1. `api/build.gradle.kts` — fully wired module

Add all required dependencies:

\- `org.springframework.boot:spring-boot-starter-web`

\- `org.springframework.boot:spring-boot-starter-validation`

\- `org.springframework.boot:spring-boot-starter-actuator`

\- `net.logstash.logback:logstash-logback-encoder:7.4` (noted in DevOps and Persistence handoffs)

\- `com.fasterxml.jackson.module:jackson-module-parameter-names`

\- Project dependencies: `implementation(project(":core-domain"))`, `implementation(project(":tax-engine"))`, `implementation(project(":persistence"))`

\- Test: `org.springframework.boot:spring-boot-starter-test`, `org.testcontainers:postgresql`

\- All licenses must be Apache 2.0 / MIT / BSD — verify before adding



Apply the Spring Boot plugin so `bootJar` works — this is required for the existing `api/Dockerfile`.



\### 2. Application entry point



`ApiApplication.java` in `com.netcompany.vat.api`:

\- Standard `@SpringBootApplication`

\- Component scan must include `com.netcompany.vat.persistence` to pick up persistence beans



`application.yml` in `api/src/main/resources/`:

```yaml

spring:

&nbsp; profiles:

&nbsp;   include: persistence

&nbsp; application:

&nbsp;   name: vat-api

server:

&nbsp; port: ${PORT:8080}

management:

&nbsp; endpoints:

&nbsp;   web:

&nbsp;     exposure:

&nbsp;       include: health,info,metrics,prometheus

&nbsp; endpoint:

&nbsp;   health:

&nbsp;     probes:

&nbsp;       enabled: true

```



\### 3. REST Controllers



Base path: `/api/v1`



---



\#### `TaxPeriodController` — `/api/v1/periods`



\*\*POST `/api/v1/periods`\*\* — open a new filing period

\- Request: `OpenPeriodRequest(jurisdictionCode, periodStart, periodEnd, filingCadence)`

\- Response: `TaxPeriodResponse`

\- Logic: validate dates, call `DkJurisdictionPlugin.calculateFilingDeadline()` to get deadline, call `TaxPeriodRepository.save(period, filingDeadline)`, log audit event `PERIOD\_OPENED`

\- Returns `201 Created`



\*\*GET `/api/v1/periods/{id}`\*\* — get a period by ID

\- Response: `TaxPeriodResponse`

\- Returns `404` if not found



\*\*GET `/api/v1/periods?jurisdictionCode=DK`\*\* — list periods for a jurisdiction

\- Response: `List<TaxPeriodResponse>`



---



\#### `TransactionController` — `/api/v1/transactions`



\*\*POST `/api/v1/transactions`\*\* — submit a transaction

\- Request: `CreateTransactionRequest(periodId, taxCode, amountExclVat, transactionDate, description, counterpartyId?)`

\- Response: `TransactionResponse`

\- Logic:

&nbsp; 1. Look up the period — return `404` if not found, `409` if period status is not OPEN

&nbsp; 2. Resolve classification via `TaxEngine` (use `RateResolver` + `ExemptionClassifier`)

&nbsp; 3. Calculate VAT via `VatCalculator`

&nbsp; 4. Save via `TransactionRepository`

&nbsp; 5. Log audit event `TRANSACTION\_CREATED`

\- Returns `201 Created`

\- `amountExclVat` on the wire is in \*\*øre\*\* (long) — document this clearly in the API



\*\*GET `/api/v1/transactions?periodId={id}`\*\* — list transactions for a period

\- Response: `List<TransactionResponse>`



---



\#### `VatReturnController` — `/api/v1/returns`



\*\*POST `/api/v1/returns/assemble`\*\* — assemble a VAT return for a period

\- Request: `AssembleReturnRequest(periodId, jurisdictionCode)`

\- Response: `VatReturnResponse`

\- Logic:

&nbsp; 1. Look up the period — `404` if not found

&nbsp; 2. Check no return already exists for this period+jurisdiction — `409` if it does

&nbsp; 3. Fetch all transactions for the period via `TransactionRepository.findByPeriod()`

&nbsp; 4. Call `VatReturnAssembler.assemble()` from tax-engine

&nbsp; 5. Save the assembled return via `VatReturnRepository.save()`

&nbsp; 6. Update period status to `LOCKED` via `TaxPeriodRepository.updateStatus()`

&nbsp; 7. Log audit event `RETURN\_ASSEMBLED`

\- Returns `201 Created`



\*\*POST `/api/v1/returns/{id}/submit`\*\* — submit return to SKAT

\- Response: `VatReturnResponse`

\- Logic:

&nbsp; 1. Look up return — `404` if not found

&nbsp; 2. Validate status is `DRAFT` — `409` otherwise

&nbsp; 3. Update status to `SUBMITTED`, set `submittedAt = now()`

&nbsp; 4. Log audit event `RETURN\_SUBMITTED`

&nbsp; 5. \*\*SKAT integration is NOT implemented yet\*\* — return a `202 Accepted` with a note in the response body that SKAT filing is pending the Integration Agent

\- Returns `202 Accepted`



\*\*GET `/api/v1/returns/{id}`\*\* — get a VAT return by ID

\- Response: `VatReturnResponse`

\- Returns `404` if not found



\*\*GET `/api/v1/returns?periodId={id}`\*\* — get return for a period

\- Response: `VatReturnResponse`



---



\#### `CounterpartyController` — `/api/v1/counterparties`



\*\*POST `/api/v1/counterparties`\*\* — register a counterparty

\- Request: `CreateCounterpartyRequest(name, vatNumber?, countryCode)`

\- Response: `CounterpartyResponse`

\- Returns `201 Created`



\*\*GET `/api/v1/counterparties/{id}`\*\*

\- Returns `404` if not found



---



\### 4. Request and Response DTOs



Create DTOs in `com.netcompany.vat.api.dto`. Rules:

\- Use Java records

\- All monetary amounts on the wire are `long` in \*\*øre\*\* — never `double` or `BigDecimal`

\- Use `String` for UUIDs on the wire (Jackson serializes/deserializes automatically)

\- Use `String` for dates (`LocalDate` serializes to ISO 8601 via Jackson JavaTimeModule)

\- Enums serialize as their `name()` string



\*\*`TaxPeriodResponse`\*\*: `id`, `jurisdictionCode`, `periodStart`, `periodEnd`, `filingCadence`, `filingDeadline`, `status`



\*\*`TransactionResponse`\*\*: `id`, `periodId`, `taxCode`, `taxClassification`, `amountExclVat`, `vatAmount`, `rateBasisPoints`, `transactionDate`, `description`, `isReverseCharge`, `counterpartyId`



\*\*`VatReturnResponse`\*\*: `id`, `periodId`, `jurisdictionCode`, `outputVat`, `inputVat`, `netVat`, `resultType`, `rubrikA`, `rubrikB`, `status`, `assembledAt`, `submittedAt`, `acceptedAt`, `skatReference`



\*\*`CounterpartyResponse`\*\*: `id`, `name`, `vatNumber`, `countryCode`



\### 5. Validation



Use Jakarta Bean Validation on all request DTOs:

\- `@NotNull` on all required fields

\- `@NotBlank` on all string fields

\- `@Positive` on `amountExclVat`

\- `@Size` constraints on string fields matching database column lengths

\- `@Pattern(regexp = "\[A-Z]{2}")` on `countryCode`



\### 6. Error handling



Create a `GlobalExceptionHandler` using `@RestControllerAdvice`:



| Exception | HTTP Status | Response body |

|---|---|---|

| `EntityNotFoundException` | 404 | `{ "error": "NOT\_FOUND", "message": "..." }` |

| `InvalidPeriodStateException` | 409 | `{ "error": "CONFLICT", "message": "..." }` |

| `MethodArgumentNotValidException` | 400 | `{ "error": "VALIDATION\_FAILED", "violations": \[...] }` |

| `Exception` (catch-all) | 500 | `{ "error": "INTERNAL\_ERROR", "message": "An unexpected error occurred" }` |



Create `EntityNotFoundException` and `InvalidPeriodStateException` as custom runtime exceptions

in `com.netcompany.vat.api.exception`.



\### 7. Tests



\*\*Unit tests\*\* (no Spring context, use Mockito):

\- `TaxPeriodControllerTest` — mock repository, test happy path + 404

\- `TransactionControllerTest` — mock TaxEngine + repository, test happy path + 409 on closed period

\- `VatReturnControllerTest` — mock assembler + repository, test assemble + submit flow



\*\*Integration tests\*\* (full Spring context + Testcontainers PostgreSQL):

\- `VatFilingFlowIT` — the full Phase 1 happy path:

&nbsp; 1. `POST /api/v1/periods` → 201

&nbsp; 2. `POST /api/v1/transactions` (×3, mix of STANDARD and ZERO\_RATED) → 201 each

&nbsp; 3. `POST /api/v1/returns/assemble` → 201, verify `netVat` is correct

&nbsp; 4. `POST /api/v1/returns/{id}/submit` → 202

&nbsp; 5. `GET /api/v1/returns/{id}` → verify status is `SUBMITTED`



This integration test is the Phase 1 proof of life. It must pass.



\### 8. OpenAPI documentation



Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.x` to `api/build.gradle.kts`.

The Swagger UI will be available at `/swagger-ui.html` in local dev.

Add `@Operation` and `@Tag` annotations to all controllers.



\## Key Constraints



1\. \*\*No domain logic in the API layer\*\* — controllers orchestrate only; all VAT calculation stays in tax-engine

2\. \*\*No monetary floats\*\* — `long` øre everywhere, on the wire and in domain objects

3\. \*\*Audit every state change\*\* — inject `AuditLogger` and call it before every status transition

4\. \*\*`TaxPeriodRepository.save()` requires a filing deadline\*\* — compute via `JurisdictionPlugin.calculateFilingDeadline()` before calling (noted in Persistence Agent handoff)

5\. \*\*Open source only\*\* — verify all dependency licenses

6\. \*\*The `api/Dockerfile` already exists\*\* — do not modify it; just ensure `bootJar` is wired in `build.gradle.kts`



\## What You Must NOT Do

\- Do not modify any file in `core-domain/`, `tax-engine/`, or `persistence/`

\- Do not implement SKAT API calls — stub the submit endpoint with `202 Accepted`

\- Do not use `double`, `float`, or `BigDecimal` for monetary values

\- Do not add JPA/Hibernate — JOOQ is the data access layer



\## Handoff Requirements

When done:

1\. `./gradlew test` passes from the project root — all modules including new API tests

2\. `VatFilingFlowIT` passes — this is the Phase 1 proof of life

3\. Update `README.md` status table — set REST API to `✅ Done`

4\. Append Session 008 to `docs/agent-sessions/session-log.md`

5\. Update `CLAUDE.md` Last Agent Session

6\. Update `api/README.md` — what it does, how to run, all endpoints, environment variables

7\. Print structured Handoff Summary



\## Next Agent Context

The Integration Agent follows. It needs:

\- The `POST /api/v1/returns/{id}/submit` endpoint already exists and returns `202 Accepted`

\- It will replace the stub with a real SKAT API call

\- The `skat-client` module is where the SKAT integration code lives

\- SKAT sandbox: `https://api-sandbox.skat.dk`

\- OIOUBL 2.1 format is being phased out May 15 2026 — use PEPPOL BIS 3.0 only

