# docs/analysis — Validated VAT Domain Knowledge

This directory contains the Business Analyst Agent's validated domain knowledge base for the Danish VAT system. All rules are cross-referenced against official SKAT sources and tagged with verification status.

## Contents

| File | Purpose |
|---|---|
| `dk-vat-rules-validated.md` | Complete validated rule reference — registration, rates, filing cadences, deduction, reverse charge, corrections, rubrik fields, ViDA roadmap |
| `implementation-risk-register.md` | 25 implementation risks ranked by likelihood and impact |
| `expert-review-questions.md` | 14 prioritised questions for a certified Danish tax advisor before go-live |

## Verification Status Tags

| Tag | Meaning |
|---|---|
| ✅ VERIFIED | Confirmed against official SKAT source; URL included |
| ⚠️ UNVERIFIED | Plausible but needs expert confirmation |
| ❌ GAP | Details unclear or missing — implementation blocked until resolved |
| 🔄 CHANGING | Subject to change (ViDA rollout, Bookkeeping Act phases) |

## Key Findings for Coding Agents

1. **Filing cadences**: Three tiers — `semi_annual` (<DKK 5M), `quarterly` (DKK 5–50M), `monthly` (>DKK 50M). The domain model currently only has `monthly | quarterly | annual` — **`semi_annual` must be added**.
2. **Single VAT rate**: Denmark has 25% only. No reduced rate exists.
3. **Non-deductible categories**: Passenger cars (blocked), restaurant meals (25% only), business gifts >DKK 100 (blocked).
4. **Monetary arithmetic**: All amounts in øre (long); rates in basis points. Never double/float.
5. **ViDA time-critical**: OIOUBL 2.1 phases out **May 15, 2026**. DRR mandatory **January 1, 2028**.

## ⚠️ Disclaimer

These documents are AI-generated and cross-referenced against public SKAT sources. They have NOT been reviewed by a certified Danish tax advisor. Do not use as legal advice.
