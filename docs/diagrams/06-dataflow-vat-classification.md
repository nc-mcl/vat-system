# Data Flow Diagram — VAT Classification Decision Tree

**What this shows:** The full classification decision tree for any transaction — determining which `TaxCode` applies. Based strictly on `docs/analysis/dk-vat-rules-validated.md` and the `DkJurisdictionPlugin` implementation. Gap items are marked visually.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

---

```mermaid
flowchart TD
    START([Transaction to classify]) --> Q_SCOPE

    Q_SCOPE{Is the transaction\nwithin Danish VAT\nscope?}
    Q_SCOPE -- No --> OOS([TaxCode = OUT_OF_SCOPE\nRate: -1 / N/A\nNo VAT obligation])
    Q_SCOPE -- Yes --> Q_INBOUND

    Q_INBOUND{Inbound purchase\nor outbound sale?}
    Q_INBOUND -- Inbound purchase --> INBOUND
    Q_INBOUND -- Outbound sale --> OUTBOUND

    subgraph INBOUND [Inbound — Purchases]
        direction TD
        PI1{Is it subject to\nReverse Charge?\nSee diagram 05}
        PI1 -- Yes --> RC([TaxCode = REVERSE_CHARGE\nRate: 2500 bp = 25%\nisReverseCharge = true\nBuyer self-assesses])
        PI1 -- No --> PI2{Is the purchase\nfor taxable\nbusiness use?}
        PI2 -- Yes → fully deductible --> STANDARD_IN([TaxCode = STANDARD\nRate: 2500 bp = 25%\nInput VAT deductible 100%])
        PI2 -- Mixed use → partial --> MIXED([TaxCode = STANDARD\nRate: 2500 bp = 25%\nInput VAT: pro-rata %\n⚠️ GAP G4: allocation method])
        PI2 -- Exempt use only --> NO_DED([TaxCode = STANDARD\nRate: 2500 bp = 25%\nInput VAT NOT deductible\ne.g. purchase for exempt activity])
        PI2 -- Non-deductible category --> NON_DED_CAT{Category?}
        NON_DED_CAT --> |Passenger car purchase| ND_CAR([Not deductible\nML §42])
        NON_DED_CAT --> |Restaurant meals| ND_REST([25% of VAT deductible only])
        NON_DED_CAT --> |Business gifts > DKK 100| ND_GIFT([Not deductible\nML §42])
        NON_DED_CAT --> |Hotel accommodation| HOTEL([100% deductible\nBusiness purpose])
    end

    subgraph OUTBOUND [Outbound — Sales]
        direction TD
        PO1{Is the supply\nexempt under\nML §13?}
        PO1 -- Yes --> EXEMPT_CAT{ML §13\nExemption category}
        PO1 -- No --> PO2

        EXEMPT_CAT --> |Healthcare — ML §13\nDoctors, dentists, physio| EXEMPT_HC([TaxCode = EXEMPT\nRate: -1 / N/A\nNo VAT charged\nNo input VAT recovery])
        EXEMPT_CAT --> |Education — ML §13\nSchools, higher education,\nvocational training| EXEMPT_EDU([TaxCode = EXEMPT\nRate: -1 / N/A])
        EXEMPT_CAT --> |Financial services — ML §13\nBanking, insurance| EXEMPT_FIN([TaxCode = EXEMPT\nRate: -1 / N/A\nLimited/no input VAT recovery])
        EXEMPT_CAT --> |Real property rental/sale — ML §13\nException: new buildings = taxable| EXEMPT_PROP([TaxCode = EXEMPT\nRate: -1 / N/A\nException: new builds at 25%])
        EXEMPT_CAT --> |Passenger transport — ML §13\nBus, train, taxi, ferry| EXEMPT_TRANS([TaxCode = EXEMPT\nRate: -1 / N/A\nException: tourist buses = 25%])
        EXEMPT_CAT --> |Artistic activities — ML §13| EXEMPT_ART([TaxCode = EXEMPT\nRate: -1 / N/A\n⚠️ GAP G6: precise\nconditions unverified])

        PO2{Is the supply\nzero-rated?}
        PO2 -- Yes --> ZERO_CAT{Zero-rate category}
        PO2 -- No --> PO3

        ZERO_CAT --> |Export of goods\noutside EU| ZERO_EXPORT([TaxCode = ZERO_RATED\nRate: 0 bp = 0%\nInput VAT recovery allowed\nReport in Rubrik C])
        ZERO_CAT --> |Intra-EU supply of goods\nto VAT-registered buyer| ZERO_EU([TaxCode = ZERO_RATED\nRate: 0 bp = 0%\nInput VAT recovery allowed\nReport in Rubrik B — goods\nFile EU-salgsangivelse])
        ZERO_CAT --> |International transport\ncertain cases| ZERO_TRANS([TaxCode = ZERO_RATED\nRate: 0 bp = 0%])

        PO3{Is it a cross-border\nB2B service sale\nto EU buyer?}
        PO3 -- Yes → buyer accounts for VAT --> RC_OUT([TaxCode = REVERSE_CHARGE\nSupplier invoices without DK VAT\nBuyer self-assesses\nReport in Rubrik B — services])
        PO3 -- No --> STD_OUT([TaxCode = STANDARD\nRate: 2500 bp = 25%\nCharge VAT on invoice])
    end

    style RC fill:#d4edda,stroke:#28a745
    style STANDARD_IN fill:#d4edda,stroke:#28a745
    style STD_OUT fill:#d4edda,stroke:#28a745
    style ZERO_EXPORT fill:#cce5ff,stroke:#004085
    style ZERO_EU fill:#cce5ff,stroke:#004085
    style ZERO_TRANS fill:#cce5ff,stroke:#004085
    style RC_OUT fill:#d4edda,stroke:#28a745
    style EXEMPT_HC fill:#e2e3e5,stroke:#6c757d
    style EXEMPT_EDU fill:#e2e3e5,stroke:#6c757d
    style EXEMPT_FIN fill:#e2e3e5,stroke:#6c757d
    style EXEMPT_PROP fill:#e2e3e5,stroke:#6c757d
    style EXEMPT_TRANS fill:#e2e3e5,stroke:#6c757d
    style EXEMPT_ART fill:#fff3cd,stroke:#ffc107
    style MIXED fill:#fff3cd,stroke:#ffc107
    style OOS fill:#e2e3e5,stroke:#6c757d
```

---

## TaxCode Summary (from `TaxCode.java`)

| TaxCode | DK Rate (basis points) | Description |
|---|---|---|
| `STANDARD` | 2500 (25.00%) | Standard Danish MOMS |
| `ZERO_RATED` | 0 (0.00%) | Exports, intra-EU goods; input VAT recovery allowed |
| `EXEMPT` | -1 (N/A) | ML §13 exempt; no VAT charged, limited/no input VAT recovery |
| `REVERSE_CHARGE` | 2500 (25.00%) | Buyer self-assesses; -1 from supplier's perspective |
| `OUT_OF_SCOPE` | -1 (N/A) | Not subject to Danish VAT |

## Reporting Field Mapping

| TaxCode | Rubrik | Notes |
|---|---|---|
| `ZERO_RATED` (intra-EU goods) | Rubrik B — goods | Must also file EU-salgsangivelse |
| `ZERO_RATED` (exports) | Rubrik C | Non-EU exports |
| `REVERSE_CHARGE` (EU goods inbound) | Rubrik A — goods | Acquisition VAT |
| `REVERSE_CHARGE` (services inbound) | Rubrik A — services | Cross-border B2B services |
| `REVERSE_CHARGE` (services outbound) | Rubrik B — services | EU B2B service sales |
| `EXEMPT` | Rubrik C | Other exempt supplies |

## Known Gaps (from `dk-vat-rules-validated.md`)

| Gap | Description | Risk |
|---|---|---|
| G4 | Partial deduction allocation methodology (turnover vs area vs usage) | Incorrect refund/payable calculation |
| G6 | Artistic activities exemption — precise ML §13 conditions | Wrong VAT treatment for creative sector |
