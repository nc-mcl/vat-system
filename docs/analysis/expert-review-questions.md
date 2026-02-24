# Expert Review Questions — Danish VAT System

## Document Status
- Last updated: 2026-02-24
- Prepared by: Business Analyst Agent (AI-generated)
- For: Certified Danish tax advisor / SKAT consultation before go-live

## Purpose
This document lists questions that must be answered by a certified Danish tax advisor or confirmed with SKAT before the VAT system goes into production. Questions are prioritised by risk of harm if the assumption is wrong.

---

## High Priority Questions

### Q1: Non-EU Purchased Services — Rubrik A Scope
**Risk if wrong:** Businesses incorrectly omit non-EU purchased services from the VAT return, or incorrectly include them in Rubrik A when they should be declared elsewhere, causing systematic misreporting on the VAT return.
**Current assumption:** We assume that services purchased from non-EU suppliers under the general B2B reverse charge rule should be reported in the same "Rubrik A — services" box as EU service purchases.
**Source of uncertainty:** SKAT's official page on "Reporting your international trade" refers specifically to EU member states for Rubrik A. The general reverse charge rule covers non-EU services but the VAT return field mapping is not explicitly confirmed for non-EU cases.
**Priority:** High

---

### Q2: Partial Deduction Allocation Methodology
**Risk if wrong:** Mixed-activity businesses (e.g. a bank offering both exempt and taxable services) may systematically over-deduct or under-deduct input VAT, leading to incorrect refunds or overpayments and potential SKAT penalties on audit.
**Current assumption:** We assume a turnover-based pro-rata is the primary allocation method. The system will calculate: `deduction % = taxable turnover / total turnover` annually.
**Source of uncertainty:** SKAT accepts multiple methodologies (turnover-based, area-based, usage-based). It is unclear which methodology is the default or whether SKAT must approve a non-turnover methodology. The MCP documents mark this as `[assumed]`.
**Priority:** High

---

### Q3: Construction Services — Domestic Reverse Charge
**Risk if wrong:** Construction service suppliers bill without VAT (believing reverse charge applies) when they should charge VAT — or vice versa — resulting in assessment errors and potential penalties for both supplier and buyer.
**Current assumption:** We have not confirmed that construction services (bygge- og anlægsydelser) are subject to domestic reverse charge in Denmark under ML §46.
**Source of uncertainty:** Construction reverse charge is common in some EU member states but is NOT on the confirmed ML §46 domestic reverse charge list that we verified for Denmark (which covers CO₂ allowances, mobile phones, metals, etc.). Some secondary sources suggest it may apply. This must be confirmed.
**Priority:** High

---

### Q4: Exemption Boundaries — Healthcare (Alternative Therapists)
**Risk if wrong:** A business offering alternative therapy (e.g. reflexology, acupuncture) may incorrectly classify its services as exempt when they are taxable, or vice versa, causing systematic VAT miscalculation.
**Current assumption:** We assume that only treatments by regulated healthcare professionals (with formal recognition/authorisation) qualify for exemption under ML §13(1)(1). Alternative therapists without formal healthcare authorisation are taxable.
**Source of uncertainty:** The SKAT page states "adequate training" is required and "treatments must aim to cure or prevent illness" — but the boundary between regulated and non-regulated practitioners is not precisely defined in the guidance fetched.
**Priority:** High

---

### Q5: Filing Deadlines — Monthly Filer Exact Dates
**Risk if wrong:** Monthly filers may submit returns late, incurring a DKK 1,400 provisional assessment per missed period.
**Current assumption:** We assume the monthly filing deadline is approximately the 25th of the month following the reporting period, based on general guidance. But SKAT publishes specific calendar dates that vary (e.g. some months the deadline is the 18th, others the 29th based on weekends/holidays).
**Source of uncertainty:** The SKAT deadline calendar shows specific dates per month (e.g. June 2025 = August 18, November 2025 = December 29). The pattern is unclear. Does the system need to consume SKAT's published calendar annually, or is there a deterministic calculation rule?
**Priority:** High

---

### Q6: Ordinary Correction — Exactly Which 3-Year Period Applies
**Risk if wrong:** The system may accept a correction it should reject (filing period too old) or reject a valid correction (miscounting the 3-year window), causing compliance failures.
**Current assumption:** We assume the 3-year window is calculated from the end of the filing period being corrected (i.e. a Q3 2022 return can be corrected until September 30, 2025).
**Source of uncertainty:** The 3-year window could be calculated from the filing submission date, the period end date, or the tax assessment date. The precise starting point for the limitation period under Skatteforvaltningsloven §31 needs confirmation.
**Priority:** High

---

## Medium Priority Questions

### Q7: VAT on Export Goods — Documentation Requirements
**Risk if wrong:** Exports may be incorrectly zero-rated without adequate documentary evidence, which SKAT could challenge on audit, reclassifying the sale as domestic and requiring output VAT payment.
**Current assumption:** We assume exports are zero-rated when supported by customs export documentation (toldangivelse) proving the goods left the EU.
**Source of uncertainty:** The specific documentation SKAT requires to substantiate zero-rating for goods exports (transport documents, customs declarations, etc.) has not been confirmed against SKAT's juridical guidance.
**Priority:** Medium

---

### Q8: VIES Validation Requirement — Timing and Caching
**Risk if wrong:** A Danish business supplies goods to a customer claiming EU VAT registration, zero-rates the supply, but the customer's VIES number is invalid. SKAT may hold the Danish supplier liable for the VAT.
**Current assumption:** We assume VIES validation at invoice creation time is sufficient, and that a cached VIES result with a timestamp is acceptable documentation for the zero-rating.
**Source of uncertainty:** SKAT's specific requirements on when VIES validation must occur (at invoice date vs. at filing date vs. both) and how long a cached VIES result is valid are not confirmed.
**Priority:** Medium

---

### Q9: Brugtmoms (Margin Scheme) — Interaction with Standard Return
**Risk if wrong:** Dealers in used goods (cars, art, antiques) using the brugtmoms margin scheme may incorrectly include full sale value in output VAT rather than only the margin, over-paying VAT.
**Current assumption:** We treat brugtmoms as a separate module ("Needs module" status) and assume standard VAT return fields should not include brugtmoms transactions.
**Source of uncertainty:** The exact field mapping for brugtmoms transactions on the standard VAT return (if any) is not confirmed. Whether brugtmoms dealers file a completely separate return or include certain aggregates in the standard return is unclear.
**Priority:** Medium

---

### Q10: Transfer of Business (Overdragelse) — VAT Treatment
**Risk if wrong:** A business asset transfer may be incorrectly taxed as a VAT-able sale when it qualifies as a non-taxable transfer of a going concern (virksomhedsoverdragelse).
**Current assumption:** We assume the "transfer of going concern" rule makes such transfers VAT-free, but the system must classify them correctly and document that they qualify.
**Source of uncertainty:** The precise conditions for a transfer to qualify as a going concern transfer (virksomhedsoverdragelse) under ML §4(5) have not been confirmed against SKAT guidance.
**Priority:** Medium

---

### Q11: Momskompensation — Eligibility and Calculation Basis
**Risk if wrong:** Non-profit organisations may fail to claim momskompensation refunds they are entitled to, leaving public money unclaimed.
**Current assumption:** Momskompensation is a separate scheme for eligible non-profit organisations (fonde, foreninger) and is not part of the standard VAT return logic.
**Source of uncertainty:** The eligibility criteria, calculation basis, and filing procedure for momskompensation have not been confirmed against the SKAT scheme documentation.
**Priority:** Medium

---

## Low Priority Questions

### Q12: Real Property Exemption — "Recently Constructed" Threshold
**Risk if wrong:** A property sale is incorrectly treated as exempt when it is in fact taxable (recently constructed), causing under-declaration of output VAT.
**Current assumption:** We assume the standard definition of "recently constructed" is a building less than 5 years old, based on general EU VAT Directive understanding.
**Source of uncertainty:** Danish law may have a different definition. The precise time threshold in Momslovens §13(1)(9) has not been confirmed.
**Priority:** Low

---

### Q13: Artistic Services Exemption — Performance vs. Goods
**Risk if wrong:** An artist's service fee is incorrectly zero-rated when the VAT exemption only applies to performances, not to physical goods (art objects, recordings).
**Current assumption:** We assume exemption applies only to the live/direct service delivery (performance), not to sales of recordings, prints, or physical art objects.
**Source of uncertainty:** The boundary between exempt artistic performance services and taxable goods/digital content sales in the artistic sector has not been confirmed against ML §13(1)(7).
**Priority:** Low

---

### Q14: Annual Pro-Rata Reporting — Submission Mechanism
**Risk if wrong:** Businesses with mixed activities fail to submit their pro-rata percentage to SKAT in the required format, resulting in a compliance gap.
**Current assumption:** We understand that from 2024, mixed-activity businesses must report their annual pro-rata percentage to SKAT. The submission mechanism (via TastSelv Erhverv, a specific form, or as part of the annual VAT return) is assumed to be a TastSelv form but has not been confirmed.
**Source of uncertainty:** The specific procedure and deadline for the 2024+ pro-rata reporting requirement was identified in search results but not confirmed from the official SKAT guidance page.
**Priority:** Low

---

## Summary

| Priority | Count | Questions |
|---|---|---|
| High | 6 | Q1, Q2, Q3, Q4, Q5, Q6 |
| Medium | 5 | Q7, Q8, Q9, Q10, Q11 |
| Low | 3 | Q12, Q13, Q14 |
| **Total** | **14** | |

## Recommended Review Process
1. Submit Q1–Q6 to a certified Danish VAT advisor (momsrådgiver) before Phase 1 implementation begins
2. Submit Q7–Q11 before Phase 1 go-live testing
3. Confirm Q12–Q14 during Phase 2 planning
4. Document the expert answers in this file and update the status tags in `dk-vat-rules-validated.md`
