# api

## What this is
Spring Boot REST API module for VAT workflows. Wires domain, tax-engine, persistence, and integration modules.

## Prerequisites
- JDK 21
- Gradle wrapper
- PostgreSQL (for full integration runtime)

## How to build
```bash
./gradlew :api:build
```

## How to run locally
```bash
./gradlew :api:bootRun
```

## How to run tests
```bash
./gradlew :api:test
```

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| SKAT_BASE_URL | No | `https://api-sandbox.skat.dk` | SKAT API base URL |
| SKAT_API_KEY | Yes (for live sandbox calls) | empty | API credential for SKAT |
| VIES_BASE_URL | No | EU VIES endpoint | VIES validation base URL |
| SPRING_DATASOURCE_URL | Yes (integration runtime) | - | PostgreSQL JDBC URL |
| SPRING_DATASOURCE_USERNAME | Yes (integration runtime) | - | Database username |
| SPRING_DATASOURCE_PASSWORD | Yes (integration runtime) | - | Database password |

## Docker
Build and run from the project root once the API Dockerfile exists.

## Kubernetes
Deploy API manifests from `/infrastructure/k8s/api/` once manifests are available.
