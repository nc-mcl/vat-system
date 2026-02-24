# C4 System Context — VAT System

**What this shows:** The VAT system and all external actors and systems it interacts with at the highest level of abstraction. No internal details — only boundaries and relationships.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

---

```mermaid
C4Context
  title System Context — Danish VAT System

  Person(business, "Business", "Files VAT returns, manages transactions, views assessments")
  Person(accountant, "Accountant", "Manages VAT filings on behalf of one or more businesses")
  Person(skatInspector, "SKAT Inspector", "Audits submitted returns and issues assessments")

  System(vatSystem, "VAT System", "Core Danish VAT filing and assessment platform. Classifies transactions, calculates VAT, manages periods, submits returns to SKAT.")

  System_Ext(skat, "SKAT API", "Danish Tax Authority (Skattestyrelsen). Receives submitted VAT returns, provides status and assessment results.")
  System_Ext(vies, "VIES", "EU VAT Information Exchange System. Validates VAT numbers of EU counterparties.")
  System_Ext(peppol, "PEPPOL / NemHandel", "European e-invoicing network. Receives and sends structured e-invoices (OIOUBL 3.0 / PEPPOL BIS 3.0).")
  System_Ext(ossPortal, "EU OSS Portal", "[Future — Phase 3] EU One Stop Shop for B2C cross-border VAT reporting.")

  Rel(business, vatSystem, "Submits transactions, files returns, views VAT position")
  Rel(accountant, vatSystem, "Manages filings on behalf of business clients")
  Rel(skatInspector, vatSystem, "Reads audit trail and submitted returns via SKAT systems")

  Rel(vatSystem, skat, "Submits VAT returns, queries filing status", "HTTPS/REST")
  Rel(vatSystem, vies, "Validates EU VAT numbers of counterparties", "HTTPS/SOAP")
  Rel(vatSystem, peppol, "Sends and receives structured e-invoices", "HTTPS/AS4")
  Rel(vatSystem, ossPortal, "Reports B2C EU cross-border sales [Phase 3]", "HTTPS/REST")

  UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

---

## Notes

- The **MCP Server** (Node.js/TypeScript) is a developer/AI tooling component, not a production actor — it is excluded from this context diagram.
- **OIOUBL 2.1** is phased out May 15, 2026; the system must support OIOUBL 3.0 and PEPPOL BIS 3.0.
- **ViDA DRR** (mandatory 2028) will add a real-time reporting relationship to SKAT; the `isVidaEnabled()` flag on the `DkJurisdictionPlugin` gates this Phase 2 flow.
- **OSS Portal** is shown as future (Phase 3) — architecture is designed to accommodate it without core changes.
