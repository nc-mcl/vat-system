# skat-client

Anti-corruption layer for all external tax authority integrations: SKAT (Danish VAT authority), VIES (EU VAT number validation), and PEPPOL BIS 3.0 (e-invoicing).

## What This Is

The `skat-client` module isolates all external system concerns from the domain model. It:

- Defines **interfaces** for each integration (`SkatClient`, `ViesClient`, `PeppolClient`)
- Provides **Phase 1 stub implementations** that simulate realistic responses without connecting to real APIs
- Provides a **`SkatMapper`** that converts domain types (øre) to SKAT wire format (DKK as `BigDecimal`)
- Will host **Phase 2 real implementations** (`SkatClientImpl`, `ViesClientImpl`) using Spring WebClient

**No business logic lives here.** This module maps and transports only.

## Module Structure

```
com.netcompany.vat.skatclient
  ├── SkatClient.java                    — submit VAT returns to SKAT
  ├── SkatClientStub.java                — Phase 1: configurable stub
  ├── ViesClient.java                    — validate EU VAT numbers via VIES
  ├── ViesClientStub.java                — Phase 1: structural pattern validation
  ├── PeppolClient.java                  — PEPPOL BIS 3.0 e-invoicing
  ├── PeppolClientStub.java              — Phase 2 placeholder (throws UnsupportedOperationException)
  ├── SkatSubmissionResult.java          — result record (skatReference, status, message, processedAt)
  ├── ViesValidationResult.java          — validation result record
  ├── SubmissionStatus.java              — ACCEPTED | REJECTED | PENDING
  ├── SkatUnavailableException.java      — thrown when SKAT is unreachable (-> 503)
  ├── ViesUnavailableException.java      — thrown when VIES is unreachable
  ├── config/
  │   ├── SkatClientProperties.java      — @ConfigurationProperties(prefix = "skat")
  │   ├── ViesClientProperties.java      — @ConfigurationProperties(prefix = "vies")
  │   └── SkatClientConfiguration.java  — @Configuration; registers stub beans
  ├── dto/
  │   ├── SkatSubmissionRequest.java     — SKAT wire format (monetary values in DKK)
  │   └── ViesValidationRequest.java     — VIES validation request
  └── mapper/
      └── SkatMapper.java                — VatReturn -> SkatSubmissionRequest (oere -> DKK)
```

## Prerequisites

- JDK 21
- Gradle wrapper

## How to Build

```bash
./gradlew :skat-client:build
```

## How to Run Tests

```bash
./gradlew :skat-client:test
```

Tests are pure unit tests — no Docker or external services required.

## Phase 1 Stub Behaviour

The stub SKAT client is controlled by the `skat.stub.response` property (default: `ACCEPTED`):

| Value | Behaviour |
|---|---|
| `ACCEPTED` | Returns a `SkatSubmissionResult` with status ACCEPTED and a generated `skatReference` |
| `REJECTED` | Returns REJECTED with a realistic Danish rejection message |
| `UNAVAILABLE` | Throws `SkatUnavailableException` — API layer returns 503 Service Unavailable |

Override via environment variable:
```bash
SKAT_STUB_RESPONSE=REJECTED ./gradlew :api:bootRun
```

The VIES stub validates VAT numbers against the structural pattern `[A-Z]{2}[0-9]{8,12}`. No real VIES API call is made.

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `SKAT_BASE_URL` | No | `https://api-sandbox.skat.dk` | SKAT API base URL (Phase 2) |
| `SKAT_API_KEY` | Phase 2 | — | SKAT API authentication key |
| `SKAT_TIMEOUT_SECONDS` | No | `30` | HTTP timeout for SKAT calls (seconds) |
| `SKAT_STUB_RESPONSE` | No | `ACCEPTED` | Stub response: ACCEPTED / REJECTED / UNAVAILABLE |
| `VIES_BASE_URL` | No | `https://ec.europa.eu/taxation_customs/vies/rest-api` | VIES API URL (Phase 2) |
| `VIES_TIMEOUT_SECONDS` | No | `10` | HTTP timeout for VIES calls (seconds) |

## Monetary Conversion

All internal values are in **oere** (long). SKAT's API expects **DKK** as `BigDecimal`.

Conversion happens **only at this boundary** in `SkatMapper.oereToDkk()`:
```
dkk = BigDecimal.valueOf(oere).divide(100, 2, HALF_UP)
```

Example: `25000 oere = 250.00 DKK`

Never use floating-point arithmetic for monetary values.

## Phase 2 Upgrade Path

To connect to the real SKAT API:

1. Implement `SkatClientImpl` using Spring `WebClient`
2. Replace `new SkatClientStub(properties)` with `new SkatClientImpl(properties)` in `SkatClientConfiguration`
3. Set `SKAT_API_KEY` and `SKAT_BASE_URL=https://api.skat.dk` in Kubernetes secrets
4. Implement `ViesClientImpl` using `WebClient` against the EU VIES REST API
5. Use PEPPOL BIS 3.0 for e-invoicing via NemHandel — **do not implement OIOUBL 2.1** (phased out May 15, 2026)

## Docker

This module is a library — no standalone Dockerfile. It is bundled into the `api` service image.

## Kubernetes

Configuration is applied via the `vat-api` ConfigMap and `vat-secrets` Secret in `/infrastructure/k8s/api/`.
Set `SKAT_API_KEY` in the secret; all other values have defaults in `application-skat.yml`.
