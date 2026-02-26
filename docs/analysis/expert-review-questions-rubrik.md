\# Expert Review Questions — Danish VAT Rubrik Routing

\*\*Document:\*\* `docs/analysis/expert-review-questions-rubrik.md`

\*\*Version:\*\* 1.0 | \*\*Date:\*\* 2026-02-26

\*\*Status:\*\* Awaiting expert review

\*\*Prepared by:\*\* VAT System Project — BA Analysis

\*\*Reference documents:\*\* A0130 VAT 3.0 Reference Case Requirements (Table 4), Momsloven (ML)



---



\## Context for the Reviewer



We are building a Danish VAT return assembly engine that maps business transactions

to the official SKAT rubrik fields. The engine currently handles standard domestic

sales and purchases correctly. The questions below concern three specific areas where

correct rubrik routing is uncertain and where an incorrect implementation would cause

systematic misreporting on submitted VAT returns.



All monetary values are in Danish kroner (DKK). All questions concern Danish VAT

obligations under Momsloven (ML) for a VAT-registered Danish company.



---



\## Gap G2 — Goods vs Services Distinction for Rubrik A and Rubrik B Routing



\### Background



SKAT's VAT return distinguishes between goods and services in both Rubrik A

(EU acquisitions/purchases) and Rubrik B (EU sales). Our engine currently routes

all reverse-charge EU transactions to Rubrik A without distinguishing between goods

and services, and routes all zero-rated EU transactions to Rubrik B without the

same distinction.



A0130 Table 4 defines the following separate boxes that require this distinction:



| Box | Description |

|-----|-------------|

| Box A — Goods (acquisitions) | Intra-EU goods purchases; VAT reported in Box 1 and Box 2 |

| Box A — Services (acquisitions) | EU services subject to Danish reverse charge; VAT in Box 1 and Box 2 |

| Box B — Goods (VAT-registered customers) | Zero-rated intra-EU goods sales to VAT-registered buyers |

| Box B — Goods (non-VAT-registered customers) | Goods sold to non-VAT-registered EU buyers (installation/assembly, new means of transport) |

| Box B — Services | Certain services to EU customers where the customer accounts for VAT |



\### Questions



\*\*G2-Q1.\*\* On the Danish SKAT momsangivelse form (as filed via TastSelv Erhverv or

virk.dk), are Rubrik A goods acquisitions and Rubrik A service acquisitions reported

in \*separate fields\*, or combined into a single Rubrik A value?



\*\*G2-Q2.\*\* Same question for Rubrik B: are goods sales and service sales to EU

customers reported in separate fields, or combined?



\*\*G2-Q3.\*\* For a Danish company purchasing software licences (SaaS) from an EU

supplier — is this reported as Rubrik A \*\*services\*\*, and is the Danish buyer required

to self-account for VAT under ML §46(1)?



\*\*G2-Q4.\*\* For a Danish company purchasing physical goods (e.g. raw materials) from

an EU supplier — is this reported as Rubrik A \*\*goods\*\*, and does acquisition VAT

apply under ML §11?



\*\*G2-Q5.\*\* Is there a transaction type that could plausibly be classified as either

goods or services depending on circumstances (e.g. software delivered on physical

media vs downloaded)? If so, what is the correct classification rule?



\*\*G2-Q6.\*\* For the "Box B — Goods (non-VAT-registered customers)" case: in practice,

which transaction types does a typical Danish B2B company encounter here? Is this

primarily distance sales, installation/assembly contracts, or new means of transport?



---



\## Gap G3 — Non-EU Services in Rubrik A



\### Background



Our engine currently only routes EU-origin reverse-charge services to Rubrik A.

We are uncertain whether services purchased from non-EU suppliers (e.g. US SaaS,

US consulting) that trigger Danish reverse charge under ML §16 should also be

reported in Rubrik A, or in a different field (e.g. Box 4 — VAT on services

purchased abroad).



A0130 Table 4 defines Box 4 as: \*"VAT amount to account for when services are

procured from non-Danish suppliers and the Danish business triggers reverse charge."\*

This appears to cover non-EU services. However, it is unclear whether the \*\*value

excluding VAT\*\* of those services should appear in Rubrik A (value field) or

somewhere else, or not at all in the value fields.



\### Questions



\*\*G3-Q1.\*\* A Danish company pays a US consulting firm for services delivered

remotely to Denmark. Under ML §16, the Danish company must self-account for VAT.

Where is the \*\*net value (ex-VAT)\*\* of this purchase reported on the momsangivelse?

Specifically: Rubrik A services, Box C, or nowhere (only the VAT amount in Box 4)?



\*\*G3-Q2.\*\* A Danish company subscribes to a US SaaS platform (e.g. Salesforce).

Same question: where is the \*\*net value\*\* reported, and where is the \*\*VAT amount\*\*

reported?



\*\*G3-Q3.\*\* Is there a meaningful distinction on the momsangivelse between:

\- Services from EU suppliers (ML §46 reverse charge), and

\- Services from non-EU suppliers (ML §16 place of supply)?



Or do both end up in the same rubrik fields?



\*\*G3-Q4.\*\* Box 4 is described as covering VAT on services purchased abroad. Does

Box 4 cover \*\*both\*\* EU and non-EU service purchases, or only non-EU? And is Box 4

a separate line from Box 1 (output VAT), or is it included within Box 1?



\*\*G3-Q5.\*\* For a Danish company with significant non-EU service purchases (e.g. a

tech company using US cloud providers), what is the correct complete reporting:

which fields on the momsangivelse are populated, with what values?



---



\## Gap G5 — Construction Reverse Charge under ML §46 (Domestic)



\### Background



Our engine handles EU cross-border reverse charge correctly. We are uncertain

whether Danish domestic reverse charge for construction services under ML §46

stk. 1 nr. 3 (the "byggeydelser" rule) is in scope and how it should be classified.



Specifically: when a Danish VAT-registered main contractor invoices a Danish

VAT-registered subcontractor for construction services, no VAT is charged on the

invoice and the buyer must self-account. This differs from the standard EU

reverse charge mechanism and may require different rubrik routing.



\### Questions



\*\*G5-Q1.\*\* Does ML §46 stk. 1 nr. 3 domestic reverse charge for construction

services ("byggeydelser") apply to a typical Danish company that is not primarily

in the construction sector, but occasionally commissions construction work

(e.g. office fit-out, building renovation)?



\*\*G5-Q2.\*\* When a Danish company receives a construction service invoice with

domestic reverse charge applied:

\- Where is the \*\*net value\*\* reported on the momsangivelse?

\- Where is the \*\*self-accounted VAT amount\*\* reported (Box 1? A separate field?)?

\- Is the \*\*input VAT deduction\*\* reported in Box 2 in the same period?



\*\*G5-Q3.\*\* What are the specific criteria that determine whether a service

qualifies as a "byggeydelse" under ML §46 stk. 1 nr. 3? Is there a published

SKAT guidance document (juridisk vejledning reference) we should implement against?



\*\*G5-Q4.\*\* Are there other domestic reverse charge categories under ML §46 that

a general Danish VAT-registered company might encounter (beyond construction),

such as scrap metal, mobile phones, or game consoles under ML §46 stk. 1 nr. 4-6?

If so, do they share the same rubrik routing as construction?



\*\*G5-Q5.\*\* Is domestic construction reverse charge commonly encountered in

practice for non-construction companies, or is it rare enough that it could be

documented as a known limitation in Phase 1 without material compliance risk?



---



\## Summary Table for Reviewer



| # | Question | Gap | Urgency | Impact if wrong |

|---|----------|-----|---------|-----------------|

| G2-Q1 | Rubrik A goods vs services — separate fields? | G2 | High | Misrouting every EU purchase |

| G2-Q2 | Rubrik B goods vs services — separate fields? | G2 | High | Misrouting every EU sale |

| G2-Q3 | SaaS from EU → Rubrik A services + ML §46? | G2/G3 | High | Incorrect reverse charge |

| G2-Q4 | Goods from EU → Rubrik A goods + ML §11? | G2 | Medium | Incorrect acquisition VAT |

| G3-Q1 | Non-EU consulting net value — Rubrik A or Box C? | G3 | High | Missing value reporting |

| G3-Q3 | EU vs non-EU services — same fields or different? | G3 | High | Systematic misreporting |

| G3-Q4 | Box 4 scope — EU and non-EU, or non-EU only? | G3 | High | Double-counting or omission |

| G5-Q1 | ML §46 domestic construction — applies to non-construction? | G5 | Medium | Missing self-accounting |

| G5-Q2 | Construction reverse charge rubrik routing | G5 | Medium | Incorrect Box 1/2 entries |

| G5-Q3 | "Byggeydelse" definition — juridisk vejledning reference | G5 | Medium | Incorrect classification |

| G5-Q5 | Is G5 rare enough to defer? | G5 | Medium | Scope decision |



---



\## Recommended Reference Materials for Reviewer



\- SKAT Juridisk Vejledning, afsnit D.A.13 (Reverse charge, ML §46)

\- SKAT Juridisk Vejledning, afsnit D.A.4.1 (Leveringsstedsregler, ydelser)

\- SKAT Momsangivelsens felter — vejledning til udfyldelse (available on skat.dk)

\- Momsloven (LBK nr. 1295 af 08/09/2020) §11, §16, §34, §46

\- Den juridiske vejledning 2024-1, D.A.14 (EU-salg uden moms)



---



\## How Answers Will Be Used



The answers to these questions will directly determine:



1\. Which transaction types the VAT engine routes to which rubrik fields

2\. Which rubrik boxes the Reporting Agent can build with confidence vs must defer

3\. Which fields need to be added to the `Transaction` domain model

&nbsp;  (`transactionType: GOODS | SERVICES`, `counterpartyCountryCode`) before

&nbsp;  the rubrik XML can be production-accurate

4\. The scope boundary between Phase 1 (current) and Phase 2 implementations



Answers in writing are preferred. A 30-minute call with a follow-up written

summary is also acceptable.



---



\*Document owner: VAT System Project\*

\*Next review: When expert answers received\*

\*Related gaps: G2, G3, G5 in `docs/analysis/implementation-risk-register.md`\*

