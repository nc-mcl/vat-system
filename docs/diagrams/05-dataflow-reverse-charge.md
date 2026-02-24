# Data Flow Diagram — Reverse Charge Decision

**What this shows:** The decision flow for determining whether reverse charge (omvendt betalingspligt) applies to a transaction. Based strictly on `docs/analysis/dk-vat-rules-validated.md` and the `DkJurisdictionPlugin` implementation.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

> **Reverse charge** means the buyer (not the supplier) is responsible for accounting for VAT. The supplier invoices without Danish VAT; the Danish buyer both declares output VAT (self-assessed) and deducts it as input VAT (if deductible). Rule source: ML §46, SKAT, EU VAT Directive B2B general rule.

---

```mermaid
flowchart TD
    START([Transaction received]) --> Q1

    Q1{Is the transaction\nan inbound purchase?}
    Q1 -- No → outbound sale --> OUTBOUND[Apply standard\noutbound rules\nZERO_RATED / STANDARD]
    Q1 -- Yes --> Q2

    Q2{Is the transaction\ncross-border?\nSupplier outside Denmark?}
    Q2 -- No → domestic supply --> Q_DOMESTIC

    Q2 -- Yes → cross-border --> Q3

    Q3{Is the buyer\na VAT-registered\nDanish business?}
    Q3 -- No → B2C buyer --> B2C[Standard rate\napplies at supplier's side\nOUT_OF_SCOPE or OSS rules]
    Q3 -- Yes → B2B buyer --> Q4

    Q4{Is it a SERVICE\nor GOODS acquisition?}

    Q4 -- Services --> Q5_SVC
    Q4 -- Goods from EU --> Q5_GOODS

    Q5_SVC{Supplier is\nEU or non-EU?}
    Q5_SVC -- EU member state --> RC_SVC_EU[REVERSE_CHARGE applies\nReport in Rubrik A — Services\nBuyer self-assesses at 25%\nML general B2B rule]
    Q5_SVC -- Non-EU supplier --> RC_SVC_NONEU[REVERSE_CHARGE applies\n⚠️ GAP G3: Rubrik A scope\nfor non-EU services\nnot fully confirmed]

    Q5_GOODS{Supplier VAT-registered\nin another EU member state?}
    Q5_GOODS -- Yes → intra-EU acquisition --> RC_GOODS[REVERSE_CHARGE applies\nAcquisition VAT — Erhvervelsesmoms\nReport in Rubrik A — Goods\nBuyer self-assesses at 25%]
    Q5_GOODS -- No → non-EU import --> IMPORT[Not reverse charge\nCustoms / Told handles VAT\nOUT_OF_SCOPE for VAT return\nRubrik C may apply]

    Q_DOMESTIC{Is the domestic supply\nsubject to ML §46\ndomestic reverse charge?}
    Q_DOMESTIC -- Yes → ML§46 category --> DOM_RC{Check ML §46 categories}
    Q_DOMESTIC -- No → domestic normal --> DOM_STD[Standard rules apply\nSTANDARD 25% or EXEMPT\nper transaction type]

    DOM_RC --> |CO₂ emission allowances| ML46[REVERSE_CHARGE applies\nDomestic ML §46]
    DOM_RC --> |Investment gold| ML46
    DOM_RC --> |Metal scrap and waste| ML46
    DOM_RC --> |Mobile phones / tablets / laptops| ML46
    DOM_RC --> |CPUs, GPUs, integrated circuits| ML46
    DOM_RC --> |Games consoles| ML46
    DOM_RC --> |Gas and electricity to distributors| ML46
    DOM_RC --> |Construction services| CONSTR_GAP[⚠️ GAP G5:\nConstruction domestic\nreverse charge NOT\nconfirmed for Denmark]

    RC_SVC_EU --> RESULT_RC([TaxCode = REVERSE_CHARGE\nrateInBasisPoints = 2500\nisReverseCharge = true])
    RC_SVC_NONEU --> RESULT_RC
    RC_GOODS --> RESULT_RC
    ML46 --> RESULT_RC

    DOM_STD --> RESULT_STD([TaxCode = STANDARD\nrateInBasisPoints = 2500\nisReverseCharge = false])
    IMPORT --> RESULT_OOS([TaxCode = OUT_OF_SCOPE\nrateInBasisPoints = -1\nisReverseCharge = false])
    B2C --> RESULT_OOS
    OUTBOUND --> RESULT_OUTBOUND([Apply outbound\nclassification flow\nsee diagram 06])
    CONSTR_GAP --> RESULT_GAP([🔴 Requires expert\nreview — do not\nauto-classify])

    style RESULT_RC fill:#d4edda,stroke:#28a745
    style RESULT_STD fill:#d4edda,stroke:#28a745
    style RESULT_OOS fill:#fff3cd,stroke:#ffc107
    style CONSTR_GAP fill:#f8d7da,stroke:#dc3545
    style RC_SVC_NONEU fill:#fff3cd,stroke:#ffc107
    style RESULT_GAP fill:#f8d7da,stroke:#dc3545
```

---

## Reverse Charge Output on the VAT Return

When `isReverseCharge = true`, the transaction contributes to two VAT return fields simultaneously:

| Contribution | Field | Description |
|---|---|---|
| Output VAT | `outputVat` | Buyer self-assessed VAT at 25% |
| Input VAT | `inputVatDeductible` | Recovered immediately (if deductible) |
| Rubrik A goods | `jurisdictionFields.rubrikAGoodsEuPurchaseValue` | Intra-EU goods acquisition value |
| Rubrik A services | `jurisdictionFields.rubrikAServicesEuPurchaseValue` | Cross-border service value |

## Known Gaps (from `dk-vat-rules-validated.md`)

| Gap | Description | Risk |
|---|---|---|
| G3 | Non-EU purchased services: Rubrik A reporting scope unclear | Possible misreporting |
| G5 | Construction domestic reverse charge not confirmed for Denmark | Missing classification |
