# Sequence Diagram — VAT Return Filing Flow

**What this shows:** The complete end-to-end flow of filing a Danish VAT return, from client transaction submission through SKAT acceptance. Shows all system actors and the messages between them.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

---

```mermaid
sequenceDiagram
  autonumber

  actor Client as Client (Business / Accountant)
  participant API as REST API<br/>(Spring Boot)
  participant Engine as Tax Engine<br/>(Java library)
  participant Plugin as DkJurisdictionPlugin
  participant Persistence as Persistence<br/>(JOOQ + PostgreSQL)
  participant Audit as Audit Trail<br/>(Immutable log)
  participant SKATClient as SKAT Client<br/>(WebClient)
  participant SKAT as SKAT API<br/>(External)

  rect rgb(220, 240, 255)
    note over Client, Persistence: Phase 1 — Transaction Ingestion
    Client->>API: POST /api/v1/transactions<br/>{transactionDate, amountExclVat, counterpartyId, ...}
    API->>API: Bean Validation (@Valid)<br/>reject if invalid → 400 Bad Request

    API->>Engine: classify(transaction, JurisdictionCode.DK)
    Engine->>Plugin: getVatRateInBasisPoints(taxCode, effectiveDate)
    Plugin-->>Engine: 2500 (25.00%) or 0 or -1
    Engine->>Plugin: isReverseChargeApplicable(transaction)
    Plugin-->>Engine: true / false
    Engine-->>API: Result<TaxClassification>

    API->>Engine: calculateVat(transaction, classification)
    Engine-->>API: Result<MonetaryAmount> (vatAmount in øre)

    API->>Audit: log(TRANSACTION_CREATED, transactionId, actorId, timestamp)
    Audit-->>API: logged

    API->>Persistence: save(transaction with classification)
    Persistence-->>API: saved transaction

    API-->>Client: 201 Created {transactionId, vatAmount, taxCode}
  end

  rect rgb(220, 255, 230)
    note over Client, Persistence: Phase 2 — Draft VAT Return Assembly
    Client->>API: POST /api/v1/periods/{periodId}/vat-return/draft
    API->>Persistence: loadTransactionsForPeriod(periodId)
    Persistence-->>API: List<Transaction>

    API->>Engine: assembleVatReturn(transactions, JurisdictionCode.DK)
    Engine->>Engine: sum outputVat (STANDARD + REVERSE_CHARGE transactions)
    Engine->>Engine: sum inputVatDeductible (deductible purchase transactions)
    Engine->>Engine: derive netVat, resultType, claimAmount
    Engine->>Engine: aggregate jurisdictionFields<br/>(rubrikA goods/services, rubrikB goods/services, rubrikC)
    Engine-->>API: Result<VatReturn> [status=DRAFT]

    API->>Audit: log(VAT_RETURN_DRAFTED, returnId, periodId, actorId)
    Audit-->>API: logged

    API->>Persistence: save(vatReturn [DRAFT])
    Persistence-->>API: saved

    API-->>Client: 200 OK {returnId, netVat, resultType, rubriks...}
  end

  rect rgb(255, 245, 210)
    note over Client, SKAT: Phase 3 — Submission Confirmation
    Client->>API: POST /api/v1/vat-returns/{returnId}/submit
    API->>Persistence: loadVatReturn(returnId) → assert status=DRAFT
    Persistence-->>API: VatReturn [DRAFT]

    API->>Engine: validateForSubmission(vatReturn, plugin)
    Engine->>Plugin: cross-field rubrik validation
    Plugin-->>Engine: Result<Void> [ok / VatRuleError]
    Engine-->>API: Result<Void>

    API->>Audit: log(VAT_RETURN_SUBMITTED, returnId, actorId, timestamp)
    Audit-->>API: logged

    API->>Persistence: updateStatus(returnId, SUBMITTED)
    Persistence-->>API: updated

    API->>SKATClient: submitReturn(vatReturn)
    SKATClient->>SKAT: POST /api/v1/vat-returns<br/>[SKAT rubrik XML payload]

    alt SKAT accepts
      SKAT-->>SKATClient: 202 Accepted {skatReference}
      SKATClient-->>API: SkatSubmissionReceipt {reference, timestamp}

      API->>Audit: log(SKAT_ACCEPTED, returnId, skatReference, timestamp)
      Audit-->>API: logged

      API->>Persistence: updateStatus(returnId, ACCEPTED)<br/>save skatReference
      Persistence-->>API: updated

      API-->>Client: 200 OK {status: ACCEPTED, skatReference}
    else SKAT rejects
      SKAT-->>SKATClient: 422 Unprocessable {errorCode, errorMessage}
      SKATClient-->>API: SkatRejection {errorCode, errorMessage}

      API->>Audit: log(SKAT_REJECTED, returnId, errorCode, timestamp)
      Audit-->>API: logged

      API->>Persistence: updateStatus(returnId, REJECTED)<br/>save rejectionReason
      Persistence-->>API: updated

      API-->>Client: 422 {status: REJECTED, errorCode, errorMessage}
    end
  end

  rect rgb(240, 220, 255)
    note over API, SKAT: Phase 2 (Future) — ViDA DRR Reporting
    note over API: DkJurisdictionPlugin.isVidaEnabled() = false until 2028.<br/>When true, each transaction is additionally routed<br/>to the DRR Reporter before acknowledgement.
  end
```

---

## Actor Summary

| Actor | Module | Notes |
|---|---|---|
| Client | External | Business or accountant using REST API |
| REST API | `/api` | Spring Boot 3.3; Bean Validation at entry |
| Tax Engine | `/tax-engine` | Pure Java; no Spring/DB dependencies |
| DkJurisdictionPlugin | `/core-domain/dk` | Stateless; registered as Spring singleton |
| Persistence | `/persistence` | JOOQ queries; Flyway-managed schema |
| Audit Trail | `/persistence` | Append-only immutable log table |
| SKAT Client | `/skat-client` | WebClient; WireMock in tests |
| SKAT API | External | `https://api.skat.dk` (sandbox: `https://api-sandbox.skat.dk`) |

## Key Invariants

- **Audit before effect:** The audit event is always written before the state change takes effect.
- **Immutability:** Once `SUBMITTED`, a return cannot be modified — corrections require a `Correction` record linking old and new `VatReturn`.
- **Result monad:** Tax Engine returns `Result<T>` (not exceptions) for expected domain errors.
- **ViDA gate:** `DkJurisdictionPlugin.isVidaEnabled()` is `false` until Phase 2 (planned 2028).
