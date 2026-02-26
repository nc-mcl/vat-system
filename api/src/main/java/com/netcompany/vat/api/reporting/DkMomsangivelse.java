package com.netcompany.vat.api.reporting;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Formatted Danish VAT return (momsangivelse) payload.
 *
 * <p>All monetary amounts are in <strong>øre</strong> (1 DKK = 100 øre).
 *
 * <p>Field naming mirrors the SKAT momsangivelse fields as defined in A0130 Table 4
 * and the {@code validate_dk_vat_filing} MCP tool schema.
 *
 * <h2>Box relationships (confirmed HIGH confidence)</h2>
 * <ul>
 *   <li>Box 1 ({@code outputVatAmount}) = domestic output VAT + Box 3 + Box 4</li>
 *   <li>Box 2 ({@code inputVatDeductibleAmount}) = total deductible input VAT</li>
 *   <li>Box 3 ({@code vatOnGoodsPurchasesAbroadAmount}) = acquisition VAT on EU goods (ML §11)</li>
 *   <li>Box 4 ({@code vatOnServicesPurchasesAbroadAmount}) = reverse-charge VAT on foreign services
 *       (both EU and non-EU, ML §46 stk. 1 nr. 1); is a component of Box 1</li>
 * </ul>
 *
 * <h2>Rubrik A — EU purchases (net values)</h2>
 * <ul>
 *   <li>{@code rubrikAGoodsEuPurchaseValue}: goods acquired from EU VAT-registered businesses (ML §11)</li>
 *   <li>{@code rubrikAServicesEuPurchaseValue}: services purchased under reverse charge from EU suppliers (ML §46 stk. 1 nr. 1)</li>
 * </ul>
 *
 * <h2>Rubrik B — EU sales (net values)</h2>
 * <ul>
 *   <li>{@code rubrikBGoodsEuSaleValue}: zero-rated goods sold to EU VAT-registered businesses (ML §34);
 *       also feeds the EU-salgsangivelse</li>
 *   <li>{@code rubrikBServicesEuSaleValue}: services sold to EU B2B buyers who account for VAT</li>
 * </ul>
 *
 * <h2>Rubrik C</h2>
 * <ul>
 *   <li>{@code rubrikCOtherVatExemptSuppliesValue}: other exempt supplies not in Rubrik A/B
 *       (ML §13 healthcare/education, non-EU exports, out-of-scope)</li>
 * </ul>
 *
 * <h2>Phase 1 limitations</h2>
 * The current {@code Transaction} model does not carry {@code transactionType} (GOODS/SERVICES)
 * or {@code counterpartyJurisdiction} (EU/NON_EU/DOMESTIC). As a result:
 * <ul>
 *   <li>REVERSE_CHARGE transactions are routed to Rubrik A services (EU services assumption)</li>
 *   <li>ZERO_RATED transactions are routed to Rubrik B services (EU services assumption)</li>
 *   <li>EXEMPT/OUT_OF_SCOPE transactions go to Rubrik C</li>
 * </ul>
 * Full goods/services and EU/non-EU routing requires Phase 2 {@code Transaction} fields.
 *
 * @param returnId                           VAT return UUID
 * @param periodId                           tax period UUID
 * @param jurisdictionCode                   "DK"
 * @param periodStart                        first day of the reporting period
 * @param periodEnd                          last day of the reporting period
 * @param outputVatAmount                    Box 1: total output VAT (øre); includes Box 3 and Box 4
 * @param inputVatDeductibleAmount           Box 2: total deductible input VAT (øre)
 * @param vatOnGoodsPurchasesAbroadAmount    Box 3: acquisition VAT on EU goods purchases (øre)
 * @param vatOnServicesPurchasesAbroadAmount Box 4: reverse-charge VAT on foreign service purchases (øre); component of Box 1
 * @param rubrikAGoodsEuPurchaseValue        Rubrik A goods: net value of EU goods acquisitions (øre)
 * @param rubrikAServicesEuPurchaseValue     Rubrik A services: net value of EU reverse-charge service purchases (øre)
 * @param rubrikBGoodsEuSaleValue            Rubrik B goods: net value of EU zero-rated goods sales (øre)
 * @param rubrikBServicesEuSaleValue         Rubrik B services: net value of EU B2B service sales (øre)
 * @param rubrikCOtherVatExemptSuppliesValue Rubrik C: other exempt/out-of-scope supply values (øre)
 * @param netVatAmount                       derived: outputVatAmount − inputVatDeductibleAmount (positive = payable)
 * @param resultType                         PAYABLE / CLAIMABLE / ZERO
 * @param claimAmount                        absolute value of netVatAmount when CLAIMABLE, else 0 (øre)
 * @param status                             VAT return lifecycle status
 * @param phase1LimitationNote              fixed note documenting Phase 1 routing approximations
 */
public record DkMomsangivelse(
        UUID returnId,
        UUID periodId,
        String jurisdictionCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        long outputVatAmount,
        long inputVatDeductibleAmount,
        long vatOnGoodsPurchasesAbroadAmount,
        long vatOnServicesPurchasesAbroadAmount,
        long rubrikAGoodsEuPurchaseValue,
        long rubrikAServicesEuPurchaseValue,
        long rubrikBGoodsEuSaleValue,
        long rubrikBServicesEuSaleValue,
        long rubrikCOtherVatExemptSuppliesValue,
        long netVatAmount,
        String resultType,
        long claimAmount,
        String status,
        String phase1LimitationNote
) {
    /**
     * Total net value of all EU purchase rubriks (Rubrik A goods + services).
     * Convenience accessor for summary display.
     */
    public long totalRubrikAValue() {
        return rubrikAGoodsEuPurchaseValue + rubrikAServicesEuPurchaseValue;
    }

    /**
     * Total net value of all EU sales rubriks (Rubrik B goods + services).
     * Convenience accessor for summary display.
     */
    public long totalRubrikBValue() {
        return rubrikBGoodsEuSaleValue + rubrikBServicesEuSaleValue;
    }
}
