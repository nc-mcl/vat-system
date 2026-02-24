# core-domain

## What this is
Pure Java 21 domain model for the VAT system. Contains records, enums, and the jurisdiction plugin SPI with no framework dependencies.

## Prerequisites
- JDK 21
- Gradle wrapper

## How to build
```bash
./gradlew :core-domain:build
```

## How to run locally
This module is a library and has no standalone runtime.

## How to run tests
```bash
./gradlew :core-domain:test
```

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| None | No | - | This module has no runtime environment variables |

## Docker
Not applicable (library module).

## Kubernetes
Not applicable (library module).
