# Implementation Risk Register — Danish VAT System

## Document Status
- Last updated: 2026-02-24
- Reviewer: Business Analyst Agent (AI-generated, requires human expert review)

## ⚠️ Important Disclaimer
Risks are assessed by an AI agent cross-referencing public SKAT sources. Risk ratings are indicative, not authoritative. A certified Danish tax advisor must review this register before go-live.

---

## Risk Ratings

**Likelihood:** High / Medium / Low
**Impact:** Critical / High / Medium / Low

- **Critical impact**: Causes incorrect tax assessments with potential legal liability for taxpayers, financial penalties, or regulatory non-compliance
- **High impact**: Causes systematic errors in filing or reporting that require correction effort and may incur penalties
- **Medium impact**: Causes inconsistencies that reduce auditability or require manual workarounds
- **Low impact**: Operational inconvenience or minor inaccuracy in non-mandatory features

---

## Risk Register

| ID | Risk | Rule Area | Likelihood | Impact | Mitigation |
|---|---|---|---|---|---|
| R01 | Wrong exemption classification — taxable supply treated as exempt | Exemptions (ML §13) | Medium | Critical | Maintain curated exemption classification dictionary; expert review of all ML §13 categories; automated cross-field validation |
| R02 | Wrong exemption classification — exempt supply charged VAT | Exemptions (ML §13) | Medium | Critical | Same as R01; unit tests for each exemption category |
| R03 | Incorrect VAT rate applied (there is only one rate; 25%) | Tax rates | Low | Critical | Hardcode 25% for standard; validate that no reduced rate exists; flag any rate other than 0/25 as an error |
| R04 | Reverse charge not triggered on B2B cross-border service | Reverse charge | Medium | Critical | Rule engine must evaluate supplier country, customer VAT status, and transaction type before applying domestic vs. reverse charge; test with non-EU service scenarios |
| R05 | Reverse charge incorrectly applied to B2C supply | Reverse charge | Medium | Critical | Customer VAT registration status must be validated (VIES) before applying reverse charge; default to domestic VAT for unvalidated customers |
| R06 | Domestic reverse charge (ML §46) category missed | Reverse charge | Medium | High | Maintain and version the domestic reverse charge category list; notify on SKAT legislative updates |
| R07 | Input VAT deducted on non-deductible category (car, gifts > DKK 100) | Input VAT deduction | Medium | High | Implement hard block on passenger car purchase VAT; implement 25% cap on restaurant VAT; enforce gift limit rule |
| R08 | Full deduction claimed on mixed-use purchases (partial deduction required) | Input VAT deduction | High | High | Pro-rata calculation is mandatory for mixed entities; implementation must enforce pro-rata percentage per entity per period; alert if deduction ratio changes significantly |
| R09 | Input VAT deducted on purchases for exempt supplies | Input VAT deduction | Medium | High | Line-level deduction_right_type must be determined before aggregation; exempt lines must set deduction_right_type=none |
| R10 | Wrong cadence assigned — semi-annual filed as quarterly or vice versa | Filing obligations | Medium | High | Cadence threshold table must include semi-annual tier (<DKK 5M); new business 18-month quarterly rule must be enforced; validate cadence at obligation creation |
| R11 | Missing zero return for inactive period | Filing obligations | Medium | High | Obligation engine must generate a return obligation for every period regardless of activity; zero declaration must be trackable and enforceable |
| R12 | Late filing not detected — provisional assessment (foreløbig fastsættelse) not triggered | Filing obligations | Medium | High | System must track due_date per obligation and flag overdue status; DKK 1,400 fee must be communicated to downstream claims system |
| R13 | EU sales list (EU-salgsangivelse) not filed or filed with wrong amounts | Reporting (EU sales) | Medium | High | System must enforce that Rubrik B (goods, EU sales list) value matches the EU-salgsangivelse for the same period; EU sales list is a separate monthly obligation |
| R14 | Rubrik A scope wrong — non-EU service imports not captured or wrongly included | Reporting fields | Medium | High | Clarify whether non-EU purchased services are in Rubrik A; implement as a confirmed rule after expert review (see Gap G3) |
| R15 | Correction applied to wrong period or filing, corrupting audit trail | Corrections | Low | High | Correction must reference specific filing_id; immutable audit trail must preserve original; correction history must be queryable |
| R16 | Ordinary correction window of 3 years not enforced | Corrections | Low | High | System must reject ordinary corrections for periods >3 years old; provide separate path for extraordinary corrections with appropriate workflow |
| R17 | Monetary arithmetic using floating point | Calculation engine | Low | Critical | All VAT amounts must use long/integer arithmetic in øre (smallest currency unit); rates stored as basis points (2500 = 25%); never use double or float |
| R18 | Rule version not captured at filing time | Audit / compliance | Medium | High | Every filing and calculation must record rule_version_id; recalculation under a different rule version must produce a traceable difference |
| R19 | Status: NOT APPLICABLE — POC scope only. No real B2G invoices processed.
Revisit if system moves to production handling real NemHandel traffic.
| R20 | ViDA DRR not implemented before January 1, 2028 deadline | ViDA / DRR | High | Critical | Architecture must be ViDA-ready; DRR reporting module must be in the backlog as a tracked milestone; EU cross-border transactions must be structured to support real-time reporting |
| R21 | VIES validation failure causes wrong customer classification | Cross-border | Medium | High | VIES lookup must be time-stamped and cached; validation failure must not silently default to B2C; must alert and halt or queue for review |
| R22 | Pro-rata percentage not reported to SKAT annually (required from 2024) | Input VAT deduction | High | Medium | System must expose a report/submission path for the annual pro-rata percentage; this is a new 2024 compliance requirement |
| R23 | Partial deduction allocation method (turnover vs usage vs area) applied incorrectly | Input VAT deduction | Medium | High | Allocation method must be explicitly configured and validated per entity; expert review required to confirm which method SKAT accepts (see Gap G4) |
| R24 | Bookkeeping records deleted within 5-year retention period | Audit / compliance | Low | Critical | All VAT records must use append-only, immutable storage; deletion must be architecturally impossible; retention check must be enforced at infrastructure level |
| R25 | OSS / IOSS sales incorrectly treated as standard domestic sales | Special schemes | Medium | High | OSS transactions must be identified by transaction type flag; routed to the OSS reporting module rather than the standard VAT return |

---

## Top Risks by Impact

### Critical Impact (must be resolved before any go-live)
1. **R17** — Floating-point monetary arithmetic: a bug that is easy to introduce and catastrophic in tax calculations
2. **R01/R02** — Wrong exemption classification: affects every transaction for exempt-sector clients (healthcare, finance, education)
3. **R20** — ViDA DRR not ready by January 2028: regulatory non-compliance with EU law

### High Impact — Systematic Filing Errors
4. **R08** — Missing pro-rata for mixed-use entities: systematic over-deduction of input VAT
5. **R04** — Reverse charge not triggered: under-declaration of VAT by buyers
6. **R10** — Wrong filing cadence: wrong return schedule, missed deadlines

### High Impact — Compliance and Audit
7. **R13** — EU sales list mismatch: SKAT cross-system validation will catch this and trigger penalties
8. **R22** — Pro-rata not reported annually: new 2024 requirement, relatively easy to miss

---

## Risk Monitoring Notes

- Risk R19 (OIOUBL 2.1 phase-out) is **time-critical**: deadline is May 15, 2026 — approximately 2.5 months from this document date.
- Risk R20 (ViDA DRR) has a **January 1, 2028** deadline — approximately 23 months. Architecture scaffolding should begin in Phase 2.
- Risks R01, R08, R23 depend on expert confirmation of allocation and classification rules before implementation.

---

## Gap Status Updates (Expert Agent Session 012 — 2026-02-26)

> Expert review of gaps G2, G3, G5 completed. See `docs/analysis/expert-review-answers-rubrik.md` for full answers.

| Gap | Area | Previous status | Updated status | Notes |
|---|---|---|---|---|
| G2 — Goods vs Services Split (rubrik A/B sub-fields) | Reporting fields | OPEN | **RESOLVED** | Rubrik A and Rubrik B each have separate goods and services sub-fields. `transactionType: GOODS \| SERVICES` and `counterpartyJurisdiction: EU \| NON_EU \| DOMESTIC` are required on `Transaction` (Phase 2 implementation). Routing rules confirmed HIGH confidence. |
| G3 — Non-EU Services in Rubrik A | Reporting fields (Risk R14) | OPEN | **PARTIALLY RESOLVED** | EU service purchases → `rubrikAServicesEuPurchaseValue` + Box 4 (HIGH confidence). Non-EU service purchases → Box 4 VAT only, net value NOT in any rubrik field (MEDIUM confidence). Professional review recommended before go-live. Risk R14 is partially mitigated. |
| G5 — Construction Reverse Charge (ML §46 stk. 1 nr. 3) | Reverse charge (Risk R06) | OPEN | **RESOLVED** | Applies to all VAT-registered buyers based on service nature, not buyer sector. Momsangivelse routing: Box 1 (self-accounted VAT) + Box 2 (deduction). NOT Box 4 (Box 4 is for foreign services only). Deferral to Phase 2 assessed as low-medium risk. Risk R06 is partially mitigated. |
