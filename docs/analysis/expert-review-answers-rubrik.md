# Expert Review Answers — Danish VAT Rubrik Routing

**Date:** 2026-02-26
**Status:** POC-grade — not a substitute for professional tax advice
**Agent:** Expert Agent, Session 012

> ⚠️ These answers are reasoned from publicly available SKAT guidance, Momsloven text,
> the validated domain knowledge base (`dk-vat-rules-validated.md`), the MCP tool
> schema (`validate_dk_vat_filing`), and A0130 Table 4 field definitions.
> They have NOT been reviewed by a certified Danish tax advisor.
> HIGH confidence answers are safe for POC use. MEDIUM confidence answers should be
> confirmed before go-live. LOW confidence answers require professional tax advice.

---

## Summary of Routing Decisions

| Transaction type | Net value field | VAT amount field | Deduction field | Confidence |
|---|---|---|---|---|
| EU goods purchase (intra-EU acquisition, ML §11) | `rubrikAGoodsEuPurchaseValue` | Box 3 (`vatOnGoodsPurchasesAbroadAmount`) | Box 2 (`inputVatDeductibleAmount`) | HIGH |
| EU services purchase (B2B reverse charge, ML §46 stk. 1 nr. 1) | `rubrikAServicesEuPurchaseValue` | Box 4 (`vatOnServicesPurchasesAbroadAmount`) | Box 2 | HIGH |
| Non-EU services purchase (reverse charge, ML §16 + ML §46 stk. 1 nr. 1) | **None** — not in any rubrik value field | Box 4 (`vatOnServicesPurchasesAbroadAmount`) | Box 2 | MEDIUM |
| EU goods sale (zero-rated, intra-EU, ML §34) | `rubrikBGoodsEuSaleValue` | n/a (zero-rated) | — | HIGH |
| EU services sale (buyer accounts for VAT) | `rubrikBServicesEuSaleValue` | n/a (zero-rated at DK level) | — | HIGH |
| Domestic standard taxable sale | n/a (value not in Rubrik A/B/C) | Box 1 (`outputVatAmount`) | — | HIGH |
| Domestic standard taxable purchase | n/a | n/a | Box 2 | HIGH |
| Exempt supply (ML §13 — healthcare, education, etc.) | `rubrikCOtherVatExemptSuppliesValue` | n/a | n/a (no right to deduct) | HIGH |
| Domestic construction services, buyer perspective (ML §46 stk. 1 nr. 3) | **None** — not in any rubrik value field | Box 1 (`outputVatAmount`, self-accounted) | Box 2 (if deductible) | HIGH |
| Domestic ML §46 stk. 1 nr. 4-6 goods (scrap, phones, consoles), buyer | **None** — not in any rubrik value field | Box 1 (`outputVatAmount`, self-accounted) | Box 2 (if deductible) | HIGH |

**Critical note on Box 4 vs Box 1:**
Box 4 ("Moms af ydelseskøb i udlandet med omvendt betalingspligt") is a **component of** Box 1
(total output VAT / salgsmoms). Self-accounted VAT on foreign service purchases flows into
Box 1 total AND is separately itemised in Box 4. Box 3 similarly flows into Box 1.
Domestic reverse charge (construction, ML §46 stk. 1 nr. 3-6) goes into Box 1 only — it is NOT
in Box 4 because Box 4 is specifically for foreign ("udlandet") service purchases.

---

## G2 — Goods vs Services Split

### G2-Q1: Are Rubrik A goods and Rubrik A service acquisitions in separate fields?

**Answer:** YES. The Danish momsangivelse as filed through TastSelv Erhverv / virk.dk has
SEPARATE sub-fields for goods and services within the Rubrik A section:
- Goods sub-field: "Varer købt i andre EU-lande" → `rubrikAGoodsEuPurchaseValue`
- Services sub-field (EU reverse charge services) → `rubrikAServicesEuPurchaseValue`

The primary visual label "Varer købt i andre EU-lande" applies to the goods sub-field only.
The services sub-field is a separate entry on the extended digital form covering B2B services
under ML §46 stk. 1 nr. 1 (EU supplier) where the Danish buyer accounts for VAT.

**Confidence:** HIGH

**Rationale:** This is confirmed by three independent sources: (1) dk-vat-rules-validated.md
sections 5.5 and 7.2 (both VERIFIED) explicitly distinguish Rubrik A goods from Rubrik A
services; (2) the MCP tool `validate_dk_vat_filing` exposes `rubrikAGoodsEuPurchaseValue` and
`rubrikAServicesEuPurchaseValue` as separate parameters, reflecting the actual SKAT digital
form field structure; (3) A0130 Table 4 explicitly lists separate "Box A — Goods (acquisitions)"
and "Box A — Services (acquisitions)" entries.

**Implementation instruction:**
- Route `TRANSACTION_TYPE = EU_GOODS_PURCHASE` → `rubrikAGoodsEuPurchaseValue`
- Route `TRANSACTION_TYPE = EU_SERVICES_PURCHASE` → `rubrikAServicesEuPurchaseValue`
- Do NOT combine EU goods and EU services into a single field. They are reported separately.
- The `Transaction` domain model requires a new field: `transactionType: GOODS | SERVICES`
  to distinguish correctly. This is Gap G2's core implementation requirement.

---

### G2-Q2: Are Rubrik B goods and service sales to EU customers in separate fields?

**Answer:** YES. Rubrik B also has SEPARATE sub-fields for goods and services:
- Goods sub-field: "Varer solgt til andre EU-lande" (ML §34 zero-rated EU goods sales) → `rubrikBGoodsEuSaleValue`
- Services sub-field (EU B2B service sales where buyer accounts for VAT) → `rubrikBServicesEuSaleValue`

**Confidence:** HIGH

**Rationale:** Confirmed by dk-vat-rules-validated.md sections 7.3-7.5 (VERIFIED) which
distinguish Rubrik B goods (EU sales list / EU-salgsangivelse) from Rubrik B services (EU B2B
service sales). The MCP tool confirms the same separation with `rubrikBGoodsEuSaleValue` and
`rubrikBServicesEuSaleValue` as distinct fields. Additionally, Rubrik B goods feeds the
EU-salgsangivelse (EU sales list) cross-validation, which only applies to goods, not services.

**Implementation instruction:**
- Route `ZERO_RATED` transactions to EU goods buyers → `rubrikBGoodsEuSaleValue`
- Route `ZERO_RATED` service transactions to EU B2B buyers → `rubrikBServicesEuSaleValue`
- The EU-salgsangivelse cross-validation (R13) applies to `rubrikBGoodsEuSaleValue` ONLY
- Same `transactionType: GOODS | SERVICES` field on `Transaction` enables this split

---

### G2-Q3: SaaS from EU supplier — Rubrik A services + ML §46?

**Answer:** YES. A Danish company purchasing SaaS (or any digital services) from an EU supplier
is subject to reverse charge under ML §46 stk. 1 nr. 1. The Danish buyer:
1. Receives an invoice from the EU supplier WITHOUT Danish VAT
2. Self-accounts for Danish VAT (25% of the net value)
3. Reports the net invoice value in `rubrikAServicesEuPurchaseValue`
4. Reports the self-accounted VAT in Box 4 (`vatOnServicesPurchasesAbroadAmount`)
5. Deducts the VAT in Box 2 (`inputVatDeductibleAmount`) if the purchase is for taxable business activities
6. Net VAT impact = zero (if fully deductible)

**Confidence:** HIGH

**Rationale:** ML §16 (B2B general rule) establishes Denmark as the place of supply for B2B
services from EU suppliers. ML §46 stk. 1 nr. 1 makes the Danish VAT-registered recipient
responsible for accounting for the VAT. SKAT guidance confirmed in dk-vat-rules-validated.md
section 5.1 (VERIFIED): "Under the general B2B place-of-supply rule, services purchased from
non-Danish suppliers are subject to reverse charge." SaaS is unambiguously a service.

**Implementation instruction:**
- Classify EU SaaS/digital service purchase as `TAX_CODE = REVERSE_CHARGE`, `transactionType = SERVICES`, `counterpartyJurisdiction = EU`
- Route net value → `rubrikAServicesEuPurchaseValue`
- Route self-accounted VAT → `vatOnServicesPurchasesAbroadAmount` (Box 4, component of Box 1)
- Deduction → `inputVatDeductibleAmount` (Box 2)

---

### G2-Q4: Physical goods from EU supplier — Rubrik A goods + ML §11?

**Answer:** YES. Intra-EU goods acquisitions from VAT-registered EU suppliers trigger acquisition
VAT (erhvervelsesmoms) under ML §11. The Danish buyer:
1. Reports the net value in `rubrikAGoodsEuPurchaseValue`
2. Reports the acquisition VAT in Box 3 (`vatOnGoodsPurchasesAbroadAmount`)
3. Deducts the acquisition VAT in Box 2 if the goods are for taxable activities
4. Net VAT impact = zero (if fully deductible)

**Confidence:** HIGH

**Rationale:** This is the core intra-EU goods acquisition mechanism. Confirmed by
dk-vat-rules-validated.md section 5.2 (VERIFIED): "Purchases of goods from VAT-registered
businesses in other EU member states... are subject to acquisition VAT (erhvervelsesmoms).
This is reported in Rubrik A — goods." ML §11 establishes the acquisition VAT liability.

**Implementation instruction:**
- Classify EU goods purchase as `TAX_CODE = REVERSE_CHARGE`, `transactionType = GOODS`, `counterpartyJurisdiction = EU`
- Route net value → `rubrikAGoodsEuPurchaseValue`
- Route acquisition VAT → `vatOnGoodsPurchasesAbroadAmount` (Box 3)
- Deduction → `inputVatDeductibleAmount` (Box 2)

---

### G2-Q5: Goods vs services classification for borderline cases (e.g. software on physical media)?

**Answer:** Apply the physical delivery test:
- Software on physical media (CD, USB, boxed product) → **GOODS** → `rubrikAGoodsEuPurchaseValue` (if EU-sourced)
- Downloadable software / SaaS / electronically supplied services → **SERVICES** → `rubrikAServicesEuPurchaseValue` (if EU-sourced)

The classification follows the mode of delivery, not the nature of the content.

**Confidence:** MEDIUM

**Rationale:** The EU VAT Directive and SKAT guidance classify electronically supplied services
(delivered over a network, essentially automated, minimal human involvement) as services, not
goods. Physical media constitutes a tangible good subject to the goods acquisition rules.
This aligns with EU Implementing Regulation 282/2011, Article 7. However, complex bundled
products (e.g. hardware with embedded licensed software, maintenance contracts that include
both on-site service and shipped components) may require case-by-case classification.
The physical delivery test handles the vast majority of cases correctly.

**Implementation instruction:**
- Add `deliveryMode: PHYSICAL | ELECTRONIC` to the `Transaction` model, or derive from `transactionType`
- For MVP: treat all `REVERSE_CHARGE` transactions as SERVICES by default (current behaviour)
- Phase 2: require explicit `transactionType: GOODS | SERVICES` for EU transactions
- Document the physical delivery rule in the transaction classification guide

---

### G2-Q6: What transaction types appear in Rubrik B goods — non-VAT-registered customers?

**Answer:** For a typical Danish B2B company, the most commonly encountered cases are:
1. **Installation/assembly contracts** — Danish company sends personnel to another EU country to install/assemble goods. The sale is zero-rated but NOT reported in the EU sales list (EU-salgsangivelse). Reports in `rubrikBGoodsEuSaleValue` (special case, no EU sales list match).
2. **New means of transport** to non-VAT-registered EU buyers — new vehicles, boats, or aircraft sold to private EU consumers where the buyer registers the transport in their home country. Zero-rated.
3. **Distance sales** where the Danish seller has registered for VAT in the destination EU country — the sale is reported in that country's VAT system, but the net value still appears in Rubrik B on the Danish momsangivelse.

For most Danish B2B companies, this scenario is rare. Distance sales to consumers have largely shifted to the One Stop Shop (OSS) scheme.

**Confidence:** MEDIUM

**Rationale:** SKAT guidance in dk-vat-rules-validated.md section 7.4 (VERIFIED) explicitly
lists these three categories as the "special cases" for Rubrik B goods that do not feed the
EU sales list. The EU sales list covers only B2B goods sales to VAT-registered buyers;
these special cases are B2C or cases where place of supply has shifted.

**Implementation instruction:**
- Add `customerVatRegistered: Boolean` to `Transaction` (or `Counterparty`)
- Phase 2: when `ZERO_RATED` + `transactionType = GOODS` + `customerVatRegistered = false` → `rubrikBGoodsEuSaleValue` (special case, no EU-salgsangivelse cross-reference)
- When `ZERO_RATED` + `transactionType = GOODS` + `customerVatRegistered = true` → `rubrikBGoodsEuSaleValue` (standard case, EU-salgsangivelse cross-reference required)

---

## G3 — Non-EU Services

### G3-Q1: Non-EU consulting services — where is the net value reported?

**Answer:** The net value of services purchased from non-EU suppliers under Danish reverse
charge is **NOT reported in any rubrik value field** on the standard momsangivelse.
Only the VAT flows appear:
- Self-accounted VAT amount → Box 4 (`vatOnServicesPurchasesAbroadAmount`)
- Input VAT deduction (if deductible) → Box 2 (`inputVatDeductibleAmount`)

The net invoice value does NOT appear in Rubrik A services (`rubrikAServicesEuPurchaseValue`),
which is an EU-specific field. It does NOT appear in Rubrik C ("Øvrige leverancer"), which
covers supplies the Danish company MAKES (its own sales/deliveries), not purchases.

**Confidence:** MEDIUM

**Rationale:** The SKAT momsangivelse field `rubrikAServicesEuPurchaseValue` is explicitly
named with "Eu" scope. The expert-agent.md ground truth note states: "Services subject to
reverse charge (both EU and non-EU) have their VAT reported in Box 4, but their net value
is typically... not reported in a value rubrik at all." The absence of a non-EU services
net value field in both the MCP tool schema and the A0130 Table 4 definition is confirmatory.
This MEDIUM confidence (rather than HIGH) is because SKAT's published guidance on the exact
treatment of the net value of non-EU service purchases in the momsangivelse value fields is
not explicit in the sources reviewed. Professional confirmation is recommended before go-live.

**Implementation instruction:**
- Non-EU service purchases: do NOT route to any rubrik value field
- Route self-accounted VAT to `vatOnServicesPurchasesAbroadAmount` (Box 4)
- Route input deduction to `inputVatDeductibleAmount` (Box 2)
- In `VatReturnAssembler`: add `counterpartyJurisdiction: EU | NON_EU | DOMESTIC` to `Transaction`
  to enable the routing decision; non-EU services contribute only to Box 4 and Box 2

---

### G3-Q2: US SaaS platform (e.g. Salesforce subscription) — net value and VAT routing?

**Answer:** Same as G3-Q1. The net subscription value is NOT reported in any rubrik value
field. The VAT treatment is:
- Self-accounted VAT (25% × net subscription value) → Box 4
- Input VAT deduction → Box 2 (if used for taxable activities; fully deductible for a
  standard B2B company)
- Net momsangivelse impact = zero

**Confidence:** MEDIUM (same reasoning as G3-Q1)

**Rationale:** US SaaS is a B2B electronic service. Under ML §16 (place of supply: B2B
general rule = buyer's country), the supply is deemed in Denmark. Under ML §46 stk. 1
nr. 1, the Danish VAT-registered buyer accounts for the VAT. The supplier origin (US vs EU)
does not change the Box 4 / Box 2 treatment for the VAT amount. The difference is solely
in whether the net value is reported in a rubrik (EU services: yes → Rubrik A services;
non-EU services: no rubrik).

**Implementation instruction:**
- Classify US SaaS as `TAX_CODE = REVERSE_CHARGE`, `transactionType = SERVICES`,
  `counterpartyJurisdiction = NON_EU`
- Route self-accounted VAT → `vatOnServicesPurchasesAbroadAmount`
- Route input deduction → `inputVatDeductibleAmount`
- Do NOT populate `rubrikAServicesEuPurchaseValue`

---

### G3-Q3: Is there a meaningful distinction on the momsangivelse between EU and non-EU services?

**Answer:** YES. There IS a meaningful and implementable distinction:

| Dimension | EU services (ML §46 stk. 1 nr. 1) | Non-EU services (ML §16 + ML §46 stk. 1 nr. 1) |
|---|---|---|
| Net value rubrik | `rubrikAServicesEuPurchaseValue` | **None** (not reported) |
| VAT amount | Box 4 | Box 4 |
| Input deduction | Box 2 | Box 2 |
| Net VAT impact (if deductible) | Zero | Zero |

Both EU and non-EU reverse-charge service purchases result in zero net VAT for a fully
deductible company (VAT self-accounted = VAT deducted). The distinction only affects
the reporting completeness of the net value fields.

**Confidence:** MEDIUM

**Rationale:** Derived from the field-level analysis above. The `rubrikAServicesEuPurchaseValue`
field name itself encodes "Eu" scope. SKAT guidance on "Varer og ydelser fra udlandet"
indicates that the Rubrik A services field specifically covers EU intra-community supplies.

**Implementation instruction:**
- Add `counterpartyJurisdiction: EU | NON_EU | DOMESTIC` to `Transaction`
- `REVERSE_CHARGE` + `SERVICES` + `EU` → `rubrikAServicesEuPurchaseValue`
- `REVERSE_CHARGE` + `SERVICES` + `NON_EU` → no rubrik value, Box 4 only
- This is a Phase 2 enhancement (requires `counterpartyJurisdiction` field on `Transaction`)

---

### G3-Q4: Does Box 4 cover both EU and non-EU service purchases? Is it inside or separate from Box 1?

**Answer:**
1. **Scope:** Box 4 covers BOTH EU and non-EU service purchases subject to reverse charge.
   The field label "Moms af ydelseskøb i udlandet med omvendt betalingspligt" uses "udlandet"
   (abroad) without restricting to EU. Both a German supplier and a US supplier trigger Box 4
   for their reverse-charge service VAT.

2. **Relationship to Box 1:** Box 4 is a COMPONENT of Box 1 (total output VAT / salgsmoms).
   The self-accounted VAT on foreign service purchases is INCLUDED within Box 1 total.
   Box 4 is a sub-line that the system must populate separately; it does not add to Box 1
   — it flows INTO it. On the momsangivelse form, Box 1 = domestic output VAT + Box 3 + Box 4
   + any other self-accounted amounts.

**Confidence:** HIGH

**Rationale:** The field label "i udlandet" (in/from abroad) covers all foreign jurisdictions.
Box 3 and Box 4 are presented as itemisations of the self-accounted output VAT within the
broader Box 1 total. This is consistent with the Danish momsangivelse form structure
described in SKAT's guidance on reporting international trade.

**Implementation instruction:**
- Ensure `vatOnServicesPurchasesAbroadAmount` receives both EU and non-EU reverse-charge
  service VAT — do NOT create separate fields for EU vs non-EU at the Box 4 level
- Ensure `outputVatAmount` (Box 1) = domestic output VAT + `vatOnGoodsPurchasesAbroadAmount`
  (Box 3) + `vatOnServicesPurchasesAbroadAmount` (Box 4); validate this cross-field constraint

---

### G3-Q5: Tech company with significant US cloud provider spend — complete momsangivelse reporting?

**Answer:** For a Danish B2B tech company spending DKK 1,000,000/year on US cloud services
(AWS, Azure, Google Cloud), with full taxable business use:

| Field | Value | Explanation |
|---|---|---|
| `rubrikAServicesEuPurchaseValue` | 0 | No EU service purchases (all US) |
| `rubrikAGoodsEuPurchaseValue` | 0 | No EU goods acquisitions |
| `vatOnServicesPurchasesAbroadAmount` (Box 4) | 250,000 | 25% × 1,000,000 DKK |
| `outputVatAmount` (Box 1) | 250,000 (at minimum) | Includes Box 4 amount |
| `inputVatDeductibleAmount` (Box 2) | 250,000 | Deduction for taxable use |
| Net VAT | 0 | Box 4 self-assessed = Box 2 deduction |

The company must also report its domestic sales in Box 1 (output VAT on taxable supplies to customers). The cloud cost does not affect net VAT but must appear in Box 4 and Box 2.

**Confidence:** MEDIUM (based on above reasoning; professional verification recommended for go-live)

**Implementation instruction:**
- Ensure `VatReturnAssembler` aggregates all non-EU service reverse-charge amounts into
  `vatOnServicesPurchasesAbroadAmount`
- Ensure the assembler does NOT set `rubrikAServicesEuPurchaseValue` for non-EU services

---

## G5 — Construction Reverse Charge

### G5-Q1: Does ML §46 stk. 1 nr. 3 apply to non-construction companies?

**Answer:** YES. ML §46 stk. 1 nr. 3 (domestic construction reverse charge — "byggeydelser")
applies to **any Danish VAT-registered buyer** who purchases qualifying construction services,
regardless of the buyer's industry sector.

A software company, a law firm, or a retail chain commissioning an office renovation from
a Danish contractor is equally subject to the reverse charge obligation as a construction
company buying sub-contracted work. The obligation is determined by:
1. The nature of the service (must qualify as a "byggeydelse" — see G5-Q3), AND
2. Both buyer and supplier must be VAT-registered in Denmark

The buyer's industry sector is irrelevant.

**Confidence:** HIGH

**Rationale:** The expert-agent.md explicitly states: "The rule applies based on the *nature
of the service*, not the buyer's sector." This is consistent with the anti-fraud purpose
of the provision (carousel VAT fraud in construction) — restricting it to construction-sector
buyers only would undermine the policy objective. SKAT juridisk vejledning D.A.13.4 confirms
the service-nature test applies to all VAT-registered buyers.

**Implementation instruction:**
- In Phase 2, when `transactionType = CONSTRUCTION_SERVICES` (domestic, Danish supplier):
  apply domestic reverse charge regardless of the buyer's `businessSector` classification
- Do NOT add a sector filter for construction reverse charge eligibility

---

### G5-Q2: Momsangivelse routing for a domestic construction reverse charge buyer?

**Answer:**

| Element | Field | Value |
|---|---|---|
| Self-accounted output VAT | Box 1 (`outputVatAmount`) | 25% × net construction service value |
| Input VAT deduction | Box 2 (`inputVatDeductibleAmount`) | Same amount (if construction work is for taxable activities) |
| Net value of service | **Not in any rubrik value field** | — |
| Box 4? | NO | Box 4 is for FOREIGN ("udlandet") service purchases only |

Critical points:
- Domestic reverse charge VAT goes into Box 1 ONLY (as part of total output VAT)
- It does NOT go into Box 4, which is specifically for foreign ("i udlandet") service purchases
- The net value of the construction service is NOT reported in Rubrik A, B, or C
  (Rubriks A/B are for EU trade; Rubrik C is for the company's own supplies/sales)

**Confidence:** HIGH

**Rationale:** Box 4 label "Moms af ydelseskøb i udlandet med omvendt betalingspligt" —
"i udlandet" (abroad/in foreign countries) excludes domestic transactions by definition.
Rubriks A and B are anchored to international trade. Rubrik C covers the company's
own supplies, not its purchases. The remaining container for self-accounted domestic
reverse charge VAT is Box 1, with a symmetric Box 2 deduction if applicable.

**Implementation instruction:**
- Phase 2: `TAX_CODE = DOMESTIC_REVERSE_CHARGE` + `transactionType = CONSTRUCTION_SERVICES`
  - Route self-accounted VAT → `outputVatAmount` (Box 1 only, NOT Box 4)
  - Route input deduction → `inputVatDeductibleAmount` (Box 2)
  - Set no rubrik value fields
- This is DIFFERENT from EU/non-EU reverse charge (which uses Box 4); the assembler
  must NOT use a single reverse-charge path for both cross-border and domestic cases

---

### G5-Q3: What qualifies as a "byggeydelse" under ML §46 stk. 1 nr. 3?

**Answer:** SKAT juridisk vejledning **D.A.13.4** is the authoritative reference. Qualifying
construction services ("byggeydelser") include:

**In scope:**
- New construction (nybygning)
- Conversion, extension, or alteration of existing buildings (ombygning, tilbygning)
- Renovation and repair work on buildings (renovering og istandsættelse)
- Electrical installation work
- Plumbing and heating/cooling (VVS-arbejde)
- HVAC installation
- Scaffolding (stilladsarbejde)
- Floor laying (gulvlægning)
- Painting and surface work carried out as part of a construction project
- Window and door installation as part of a construction project
- Demolition (nedrivning)

**Not in scope:**
- Standalone cleaning or maintenance services (without physical alteration)
- Facility management and caretaking
- Purely advisory/engineering services without physical construction component
- Equipment maintenance where no structural change occurs

**Key criterion:** The service must involve physical alteration, construction, or repair of
a building or structure. The contractor must be in the business of providing construction
services (i.e., a bygherre engaging a general contractor, electrical contractor, etc.).

**Confidence:** MEDIUM (general principle HIGH; specific edge cases such as combined
maintenance/renovation contracts or modular/prefabricated installations require D.A.13.4
lookup and potentially professional advice)

**Rationale:** SKAT juridisk vejledning D.A.13.4 is the published SKAT reference for
ML §46 stk. 1 nr. 3. The core test (physical building work by a construction contractor)
is well-established in Danish tax practice since the domestic reverse charge rule was
introduced in the 2012 Finance Act.

**Implementation instruction:**
- Phase 2: introduce `TransactionCategory.CONSTRUCTION_SERVICES` as a new enum value
- Document in the user guide that classification as CONSTRUCTION_SERVICES requires:
  (a) the service is physically altering a building, AND
  (b) the supplier is a VAT-registered construction contractor
- Reference D.A.13.4 in the classification guide

---

### G5-Q4: Other ML §46 domestic reverse charge categories and their routing?

**Answer:** The following domestic reverse charge categories also exist under ML §46 stk. 1:
- **nr. 4**: Scrap metal, waste metal, and certain processed metals
- **nr. 5**: Mobile phones, tablet computers, laptops (when purchased for resale)
- **nr. 6**: Game consoles, video games, CPUs/GPUs (when purchased for resale)
- Plus: CO₂ emission allowances, investment gold (nr. 1-2), gas/electricity to distributors (nr. 7)

**All domestic ML §46 categories share the same momsangivelse routing as construction:**
- Self-accounted output VAT → Box 1 (NOT Box 4)
- Input deduction → Box 2
- Net value → NOT in any rubrik value field

**Confidence:** HIGH

**Rationale:** The routing logic is identical for all domestic reverse charge categories:
the VAT is self-accounted (Box 1) and deducted (Box 2) with no international trade rubrik
applicable. The only variant is the specific ML §46 paragraph number, which determines
whether reverse charge applies at all (the classification step), not how it is reported
(the routing step).

**Implementation instruction:**
- Phase 2: all `TAX_CODE = DOMESTIC_REVERSE_CHARGE` transactions, regardless of category
  (construction, scrap, phones), use the same routing: Box 1 + Box 2 only
- Consider a `domReverseChargeCategory: CONSTRUCTION | SCRAP | ELECTRONICS | OTHER` sub-field
  on `Transaction` for audit/reporting purposes, but the momsangivelse routing is identical

---

### G5-Q5: Is domestic construction reverse charge rare enough to defer to Phase 2?

**Answer:** YES — safe to defer to Phase 2 as a documented known limitation, with the
following qualification:

Construction reverse charge is **moderately common** (any company with permanent premises
will periodically encounter it) but is **operationally predictable** (contractors in the
construction sector know to apply it, and will notify buyers). It is NOT a high-frequency
daily transaction type for a typical B2B services or technology company.

**Deferral risk level:** LOW-MEDIUM. The risk is that a company using the system for a
period that includes a significant office renovation will mis-report the reverse charge
VAT. However, since the net VAT impact is zero (self-accounted = deducted for a fully
taxable company), the financial exposure is the reporting omission, not a payment error.
SKAT can request correction within the 3-year ordinary correction window.

**Confidence:** HIGH (the deferral recommendation, not the tax rule itself)

**Rationale:** Phase 1 is a proof of concept focused on core Danish VAT mechanics. The
domestic reverse charge categories (construction, scrap, phones) represent a well-defined
Phase 2 enhancement that can be cleanly added as new `TransactionCategory` values without
architectural changes.

**Implementation instruction:**
- Document in Phase 1 release notes: "Domestic reverse charge under ML §46 (construction,
  scrap metal, electronics) is not supported in Phase 1. Transactions of these types should
  be excluded from the system and filed manually until Phase 2."
- Add DOMESTIC_REVERSE_CHARGE as a Phase 2 `TaxCode` backlog item

---

## Gap Status After This Review

| Gap | Status | Remaining uncertainty |
|---|---|---|
| G2 — Goods vs Services Split | **RESOLVED** | None for the routing decision itself. Implementation requires new `transactionType: GOODS \| SERVICES` and `counterpartyJurisdiction: EU \| NON_EU \| DOMESTIC` fields on `Transaction` (Phase 2). |
| G3 — Non-EU Services | **PARTIALLY RESOLVED** | EU services → Rubrik A services (HIGH confidence). Non-EU services → Box 4 VAT only, no net value rubrik (MEDIUM confidence). Recommend professional confirmation of the non-EU net value treatment before go-live. |
| G5 — Construction Reverse Charge | **RESOLVED** | Applies to all buyers (nature-of-service test). Box 1 + Box 2 routing (not Box 4). Deferral to Phase 2 is low risk. D.A.13.4 is the authoritative classification reference. |

---

## Instructions for Reporting Agent

The following routing rules are confirmed for implementation. The Reporting Agent should read
this document before building any rubrik XML serialisation logic.

### Confirmed for implementation (HIGH confidence)

1. **Rubrik A has two sub-fields:** `rubrikAGoodsEuPurchaseValue` (EU goods acquisitions,
   ML §11) and `rubrikAServicesEuPurchaseValue` (EU service purchases under reverse charge,
   ML §46 stk. 1 nr. 1). These are SEPARATE fields in the SKAT digital form.

2. **Rubrik B has two sub-fields:** `rubrikBGoodsEuSaleValue` (EU zero-rated goods sales,
   ML §34, feeds EU-salgsangivelse) and `rubrikBServicesEuSaleValue` (EU B2B service sales
   where buyer accounts for VAT). SEPARATE fields.

3. **Box 4 covers both EU and non-EU service reverse charge VAT.** Route all
   `vatOnServicesPurchasesAbroadAmount` amounts here regardless of supplier origin.

4. **Box 4 is a component of Box 1.** Ensure assembler includes Box 4 within Box 1 total.

5. **Domestic reverse charge (ML §46 all categories) uses Box 1 only** — NOT Box 4.
   Box 4 is for foreign ("i udlandet") service purchases only.

6. **EU goods acquisitions:** `rubrikAGoodsEuPurchaseValue` + `vatOnGoodsPurchasesAbroadAmount` (Box 3).

7. **EU service acquisitions:** `rubrikAServicesEuPurchaseValue` + `vatOnServicesPurchasesAbroadAmount` (Box 4).

### Confirmed for implementation at MEDIUM confidence (professional review recommended)

8. **Non-EU service acquisitions:** Box 4 VAT only. Net value NOT reported in any rubrik.
   Do NOT route to `rubrikAServicesEuPurchaseValue`.

9. **Construction reverse charge buyer (domestic):** Box 1 self-accounted VAT + Box 2
   deduction. No rubrik value field. Box 4 not applicable.

### Deferred to Phase 2

10. The `Transaction` domain model requires two new fields for correct EU rubrik routing:
    - `transactionType: GOODS | SERVICES`
    - `counterpartyJurisdiction: EU | NON_EU | DOMESTIC`
    These fields are Phase 2 requirements. Phase 1 Reporting Agent can use the current
    REVERSE_CHARGE / ZERO_RATED tax codes with the caveat that goods/services split
    and EU/non-EU split are approximated.

---

## Handoff to Reporting Agent

The following routing decisions are confirmed for implementation:
- Rubrik A goods vs services: SEPARATE fields (HIGH confidence) — implement immediately
- Rubrik B goods vs services: SEPARATE fields (HIGH confidence) — implement immediately
- Box 4 covers EU + non-EU reverse-charge service VAT (HIGH confidence) — implement
- Box 4 is component of Box 1 (HIGH confidence) — implement cross-field constraint
- Domestic reverse charge (construction, etc.) → Box 1 only, not Box 4 (HIGH confidence) — implement for Phase 2

The following are deferred pending production-grade review:
- Non-EU services net value treatment (MEDIUM confidence: "not in any rubrik" reasoning
  is sound but not explicitly confirmed in published SKAT guidance reviewed)

The Reporting Agent should read `expert-review-answers-rubrik.md` before building any
rubrik XML serialisation logic. The key architectural requirement is that `Transaction`
must carry `transactionType` and `counterpartyJurisdiction` for correct full rubrik routing.
In Phase 1, the Reporting Agent may produce a correct rubrik XML using the current
`TaxCode` values with explicit Phase 1 limitation notes in the generated report.
