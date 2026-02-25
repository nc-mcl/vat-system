# tax-engine

## What this is
Pure Java VAT calculation and classification library. Implements deterministic tax rules with zero infrastructure
dependencies — no Spring, no JOOQ, no HTTP. Given the same inputs and rule version, output is always identical.

Implemented by the **Domain Rules Agent** on 2026-02-24.

## Components

| Class | Responsibility |
|---|---|
| `RateResolver` | Resolves VAT rate in basis points from the jurisdiction plugin for a given tax code and date |
| `VatCalculator` | Pure integer arithmetic: output VAT, input deduction, net VAT, result type |
| `ReverseChargeEngine` | Applies and validates reverse charge (omvendt betalingspligt) |
| `ExemptionClassifier` | Confirms a transaction's VAT classification against the plugin's rate schedule |
| `FilingPeriodCalculator` | Computes filing cadence, deadlines, and overdue status |
| `VatReturnAssembler` | Aggregates transactions into a draft `VatReturn` with rubrik field totals |
| `TaxEngine` | Facade that composes all components bound to a single jurisdiction plugin |

Supporting value types (in this module):
- `VatResult` — sealed: `Payable(amount) | Claimable(amount) | Zero`
- `ReverseChargeResult` — output VAT + input VAT + base amount for a reverse-charge transaction

Types from `core-domain` used: `Result<T>`, `VatRuleError`, `Transaction`, `TaxPeriod`, `VatReturn`, `JurisdictionPlugin`, `MonetaryAmount`, `TaxCode`, `FilingCadence`.

## Prerequisites
- JDK 21
- Gradle 8.x (use `gradlew` / `gradlew.bat` in the project root)

**Build environment note (Windows):** If the default Gradle user home is on a BitLocker-locked drive, set:
```bat
set GRADLE_USER_HOME=C:\Temp\gradle-user-home
set JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr
```

## How to build
```bash
./gradlew :tax-engine:build
```

## How to run locally
This module is a library and has no standalone runtime.

## How to run tests
```bash
./gradlew :tax-engine:test
```

Test results: `tax-engine/build/reports/tests/test/index.html`

**Test coverage: 68 tests, 6 test classes, 0 failures** (verified 2026-02-24).

| Test class | Tests |
|---|---:|
| `RateResolverTest` | 9 |
| `VatCalculatorTest` | 16 |
| `ReverseChargeEngineTest` | 11 |
| `ExemptionClassifierTest` | 6 |
| `FilingPeriodCalculatorTest` | 14 |
| `VatReturnAssemblerTest` | 12 |
| **Total** | **68** |

## Environment variables
| Variable | Required | Default | Description |
|---|---|---|---|
| None | No | — | Library module — no runtime environment variables |

## Key design decisions
- **No floating point** — all amounts in øre (`long`), all rates in basis points (2500 = 25.00%)
- **`Result<T>` pattern** — errors are returned as `Result.err(VatRuleError)`, never thrown as exceptions
- **Jurisdiction-agnostic** — all country-specific logic comes from `JurisdictionPlugin`; no hardcoded DK values
- **MVP rubrik routing** — `REVERSE_CHARGE` → Rubrik A services; `ZERO_RATED` → Rubrik B services;
  `EXEMPT/OUT_OF_SCOPE` → Rubrik C. Full goods/services/EU split requires a `transactionType` field on `Transaction`

## Known gaps (for next agent)
1. Rubrik A/B goods vs services split requires a `transactionType` (GOODS/SERVICES) field on `Transaction`,
   plus counterparty country for full EU routing.

## Docker
Not applicable (library module).

## Kubernetes
Not applicable (library module).
