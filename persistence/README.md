# persistence

## What this is
Persistence layer for VAT data using JOOQ and Flyway. Contains repository implementations and database migration assets.

## Prerequisites
- JDK 21
- Docker (for PostgreSQL/Testcontainers)
- Gradle wrapper

## How to build
```bash
./gradlew :persistence:build
```

## How to run locally
This module is consumed by the API service and is not run standalone.

## How to run tests
```bash
./gradlew :persistence:test
```

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| SPRING_DATASOURCE_URL | Yes (integration runtime) | - | PostgreSQL JDBC URL |
| SPRING_DATASOURCE_USERNAME | Yes (integration runtime) | - | Database username |
| SPRING_DATASOURCE_PASSWORD | Yes (integration runtime) | - | Database password |

## Docker
Not applicable for this module directly (used by API container and test containers).

## Kubernetes
Not applicable for this module directly (deployed as part of API service stack).
