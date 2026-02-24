# skat-client

## What this is
Integration adapter module for external authority systems (SKAT, VIES, PEPPOL). Provides anti-corruption boundary between VAT domain and external protocols.

## Prerequisites
- JDK 21
- Gradle wrapper

## How to build
```bash
./gradlew :skat-client:build
```

## How to run locally
This module is a library consumed by `api` and does not run standalone.

## How to run tests
```bash
./gradlew :skat-client:test
```

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| SKAT_BASE_URL | No | `https://api-sandbox.skat.dk` | SKAT API base URL |
| SKAT_API_KEY | Yes (for authenticated calls) | empty | SKAT credential |
| VIES_BASE_URL | No | EU VIES endpoint | VIES validation endpoint |

## Docker
Not applicable (library module).

## Kubernetes
Not applicable (library module).
