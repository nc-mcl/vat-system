# api — VAT System REST API

## What this is

The `api` module is the deployable Spring Boot application that exposes the VAT System
as an HTTP service. It wires together the `core-domain`, `tax-engine`, and `persistence`
modules and provides REST endpoints for the full VAT filing lifecycle.

### Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/periods` | Open a new VAT filing period |
| `GET` | `/api/v1/periods/{id}` | Get a period by ID |
| `GET` | `/api/v1/periods?jurisdictionCode=DK` | List periods for a jurisdiction |
| `POST` | `/api/v1/transactions` | Submit a VAT transaction to an open period |
| `GET` | `/api/v1/transactions?periodId={id}` | List transactions for a period |
| `POST` | `/api/v1/returns/assemble` | Assemble a draft VAT return for a period |
| `POST` | `/api/v1/returns/{id}/submit` | Submit a return to SKAT (stub — Integration Agent pending) |
| `GET` | `/api/v1/returns/{id}` | Get a VAT return by ID |
| `GET` | `/api/v1/returns?periodId={id}` | Get return for a period |
| `POST` | `/api/v1/counterparties` | Register a counterparty |
| `GET` | `/api/v1/counterparties/{id}` | Get a counterparty by ID |
| `GET` | `/actuator/health/liveness` | Kubernetes liveness probe |
| `GET` | `/actuator/health/readiness` | Kubernetes readiness probe |
| `GET` | `/actuator/metrics` | Prometheus metrics |
| `GET` | `/swagger-ui.html` | Swagger UI (local dev only) |

### Monetary amounts

**All monetary amounts on the wire are in øre (1 DKK = 100 øre).**
For example, 10,000 DKK = 1,000,000 øre.

---

## Prerequisites

- JDK 21
- Gradle wrapper (`./gradlew`)
- PostgreSQL 16 (or Docker Compose)

---

## How to build

```bash
unset GRADLE_OPTS
./gradlew :api:build
```

This compiles the module and runs unit tests. The `bootJar` task produces
`api/build/libs/api-0.1.0-SNAPSHOT.jar`.

---

## How to run locally

### Option A — Docker Compose (recommended)

```bash
docker compose up
```

Starts PostgreSQL, Adminer, and the API on port 8080.

### Option B — Gradle bootRun

Set environment variables first (or let them default to docker-compose values):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/vatdb
export DB_USER=vat
export DB_PASSWORD=vat_dev_password

unset GRADLE_OPTS
./gradlew :api:bootRun
```

The API starts on `http://localhost:8080`. Swagger UI is at `http://localhost:8080/swagger-ui.html`.

---

## How to run tests

### Unit tests (no Docker required)

```bash
unset GRADLE_OPTS
./gradlew :api:test --tests "com.netcompany.vat.api.controller.*"
```

### Integration tests (Docker required)

```bash
unset GRADLE_OPTS
./gradlew :api:test
```

`VatFilingFlowIT` requires Docker to be accessible from the JVM process.
It skips automatically if Docker is not available (`@Testcontainers(disabledWithoutDocker = true)`).

In CI (Linux with Docker service container), all tests run automatically.

**Windows/Docker Desktop:** Enable "Expose daemon on tcp://localhost:2375" in Docker Desktop
settings for local IT runs.

---

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | No | `jdbc:postgresql://localhost:5432/vatdb` | PostgreSQL JDBC URL |
| `DB_USER` | No | `vat` | Database username |
| `DB_PASSWORD` | No | `vat_dev_password` | Database password |
| `PORT` | No | `8080` | HTTP port |
| `SKAT_BASE_URL` | No | `https://api-sandbox.skat.dk` | SKAT API base URL (Integration Agent) |
| `SKAT_API_KEY` | Yes (SKAT calls) | — | SKAT API credential (Integration Agent) |

---

## Module structure

```
api/
  src/main/java/com/netcompany/vat/api/
    ApiApplication.java                 ← Spring Boot entry point
    config/
      JurisdictionRegistry.java         ← Plugin registry + TaxEngine factory
    controller/
      TaxPeriodController.java          ← /api/v1/periods
      TransactionController.java        ← /api/v1/transactions
      VatReturnController.java          ← /api/v1/returns
      CounterpartyController.java       ← /api/v1/counterparties
    dto/                                ← Request/response records (all monetary in øre)
    exception/
      EntityNotFoundException.java      ← → 404
      InvalidPeriodStateException.java  ← → 409
    handler/
      GlobalExceptionHandler.java       ← Maps exceptions to JSON error responses
  src/test/java/com/netcompany/vat/api/
    controller/                         ← Unit tests (Mockito, no Spring context)
    it/
      VatFilingFlowIT.java              ← Phase 1 proof-of-life (Testcontainers)
```

---

## Docker

The `Dockerfile` is a multi-stage build (DevOps Agent). Build with:

```bash
docker build -t vat-api:latest .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/vatdb \
  -e DB_USER=vat \
  -e DB_PASSWORD=vat_dev_password \
  vat-api:latest
```

---

## Kubernetes

Manifests are in `infrastructure/k8s/api/`. Deploy with:

```bash
kubectl apply -f infrastructure/k8s/api/
```

Liveness probe: `GET /actuator/health/liveness`
Readiness probe: `GET /actuator/health/readiness`

---

## Design notes

- **No domain logic in the API layer** — controllers orchestrate only; all VAT calculation lives in `tax-engine`
- **Audit first** — `AuditLogger.log()` is called before every state-changing database operation
- **Monetary values** — always `long` in øre on the wire and in domain objects; never `double` or `BigDecimal`
- **SKAT submission stub** — `POST /api/v1/returns/{id}/submit` returns `202 Accepted` with a note that SKAT filing is pending the Integration Agent
- **Period locking** — after return assembly, the period transitions to `FILED` to prevent further transaction additions
