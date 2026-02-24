# Jurisdiction Plugin Architecture Diagram

**What this shows:** How the plugin system works — the `JurisdictionPlugin` interface, its current implementation (`DkJurisdictionPlugin`), the `JurisdictionRegistry`, and how the `TaxEngine` resolves plugins at runtime. Future jurisdiction placeholders show that adding a new country requires a new box only — zero changes to existing code.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

> **Key invariant (ADR-003):** Adding a new country = new plugin class only. Zero changes to `tax-engine`, `persistence`, API routing, audit trail, or any other jurisdiction's code.

---

## Plugin System Overview

```mermaid
graph TD
    subgraph CORE_DOMAIN ["core-domain module\n(com.netcompany.vat.coredomain)"]
        direction TB

        INTERFACE["«interface»\nJurisdictionPlugin\n─────────────────────\n+getCode(): JurisdictionCode\n+getVatRateInBasisPoints(TaxCode, LocalDate): long\n+determineFilingCadence(MonetaryAmount): FilingCadence\n+calculateFilingDeadline(TaxPeriod): LocalDate\n+isReverseChargeApplicable(Transaction): boolean\n+isVidaEnabled(): boolean\n+getAuthorityName(): String"]

        DK["«record / final class»\nDkJurisdictionPlugin\n─────────────────────\ncode: DK\nstandardRate: 2500 bp = 25%\nMonthly threshold: >50M DKK\nQuarterly: 5M–50M DKK\nSemi-annual: <5M DKK\nDeadline: 10th of following month\nViDA: false (until 2028)\nAuthority: SKAT"]

        NO_FUTURE["«future — Phase 3»\nNoJurisdictionPlugin\n─────────────────────\ncode: NO\nstandardRate: 2500 bp = 25%\nreducedRate: 1500 bp = 15% (food)\nAuthority: Skatteetaten\nViDA: false"]

        DE_FUTURE["«future — Phase 3»\nDeJurisdictionPlugin\n─────────────────────\ncode: DE\nstandardRate: 1900 bp = 19%\nreducedRate: 700 bp = 7%\nAuthority: Finanzamt / BZSt\nViDA: false"]

        DK -->|implements| INTERFACE
        NO_FUTURE -->|implements| INTERFACE
        DE_FUTURE -->|implements| INTERFACE

        REGISTRY["JurisdictionRegistry\n─────────────────────\n+register(plugin: JurisdictionPlugin)\n+resolve(code: JurisdictionCode): JurisdictionPlugin\n─────────────────────\nHolds: Map<JurisdictionCode, JurisdictionPlugin>"]

        REGISTRY -->|contains| DK
        REGISTRY -.->|will contain — Phase 3| NO_FUTURE
        REGISTRY -.->|will contain — Phase 3| DE_FUTURE
    end

    subgraph TAX_ENGINE ["tax-engine module\n(com.netcompany.vat.taxengine)"]
        ENGINE["TaxEngine\n─────────────────────\n+classify(transaction, plugin): Result<TaxClassification>\n+calculateVat(transaction, classification): Result<MonetaryAmount>\n+assembleVatReturn(transactions, plugin): Result<VatReturn>\n─────────────────────\nDepends only on JurisdictionPlugin interface\nNever imports DkJurisdictionPlugin directly"]
    end

    subgraph API_MODULE ["api module — Spring @Configuration"]
        CONFIG["JurisdictionRegistryConfig\n─────────────────────\n@Bean JurisdictionRegistry\n  registry.register(new DkJurisdictionPlugin())\n  // Phase 3: registry.register(new NoJurisdictionPlugin())\n  // Phase 3: registry.register(new DeJurisdictionPlugin())"]
    end

    ENGINE -->|resolves plugin via| REGISTRY
    ENGINE -->|calls methods on| INTERFACE
    CONFIG -->|creates and populates| REGISTRY

    style INTERFACE fill:#e3f2fd,stroke:#1565C0,stroke-width:2px
    style DK fill:#e8f5e9,stroke:#2E7D32,stroke-width:2px
    style NO_FUTURE fill:#f3e5f5,stroke:#6A1B9A,stroke-dasharray: 8 4
    style DE_FUTURE fill:#f3e5f5,stroke:#6A1B9A,stroke-dasharray: 8 4
    style REGISTRY fill:#fff8e1,stroke:#F57F17,stroke-width:2px
    style ENGINE fill:#fce4ec,stroke:#880E4F,stroke-width:2px
    style CONFIG fill:#e0f2f1,stroke:#004D40
    style CORE_DOMAIN fill:#f9fbe7,stroke:#827717
    style TAX_ENGINE fill:#fbe9e7,stroke:#BF360C
    style API_MODULE fill:#e8eaf6,stroke:#1A237E
```

---

## Adding a New Jurisdiction — What Changes

```mermaid
flowchart LR
    subgraph TODAY ["Phase 1 — Today"]
        DK1["DkJurisdictionPlugin\n✅ Implemented"]
    end

    subgraph PHASE3 ["Phase 3 — Adding Norway"]
        NO1["NoJurisdictionPlugin\n🆕 New class only"]
        NOTE1["1. Create NoJurisdictionPlugin\n   in core-domain/no/\n2. Add NO to JurisdictionCode enum\n3. Register in API @Configuration\n\nZERO changes to:\n• tax-engine logic\n• persistence schema\n• API routing\n• audit trail\n• DkJurisdictionPlugin\n• any other code"]
    end

    DK1 -->|unchanged| PHASE3
    NO1 --- NOTE1

    style DK1 fill:#e8f5e9,stroke:#2E7D32
    style NO1 fill:#e3f2fd,stroke:#1565C0
    style NOTE1 fill:#fff9c4,stroke:#F9A825
```

---

## ViDA Layering (Phase 2)

```mermaid
flowchart TD
    TX["Transaction received"] --> RESOLVE["Resolve plugin\nregistry.resolve(jurisdictionCode)"]
    RESOLVE --> CLASSIFY["classify + calculateVat\nvia TaxEngine"]
    CLASSIFY --> VIDA_CHECK{plugin.isVidaEnabled()?}

    VIDA_CHECK -- false\nDK until 2028 --> PERSIST["Persist transaction\n+ audit log"]
    VIDA_CHECK -- true\nPhase 2 future --> DRR["Route to DRR Reporter\nReal-time reporting\nto tax authority API"]
    DRR --> PERSIST

    PERSIST --> ACK["Acknowledge to client"]

    style VIDA_CHECK fill:#fff3cd,stroke:#ffc107
    style DRR fill:#f3e5f5,stroke:#9C27B0,stroke-dasharray: 8 4
```

---

## Interface Contract Summary

| Method | DK Implementation | Purpose |
|---|---|---|
| `getCode()` | `JurisdictionCode.DK` | Registry lookup key |
| `getVatRateInBasisPoints(STANDARD, date)` | `2500` | 25.00% MOMS |
| `getVatRateInBasisPoints(EXEMPT, date)` | `-1` | Not applicable |
| `determineFilingCadence(>50M DKK)` | `MONTHLY` | High-turnover filers |
| `determineFilingCadence(5M–50M DKK)` | `QUARTERLY` | Mid-range filers |
| `determineFilingCadence(<5M DKK)` | `SEMI_ANNUAL` | Small filers |
| `calculateFilingDeadline(period)` | `period.endDate + 1 month, 10th` | 10th of following month |
| `isReverseChargeApplicable(tx)` | `tx.classification.taxCode == REVERSE_CHARGE` | Delegate to classification |
| `isVidaEnabled()` | `false` | Phase 2 gate |
| `getAuthorityName()` | `"SKAT"` | Audit log header |
