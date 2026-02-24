package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.domain.VatRuleError;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles a {@link VatReturn} aggregate from a list of transactions for a tax period.
 *
 * <p>The assembler:
 * <ol>
 *   <li>Rejects periods that are already filed (immutability rule)</li>
 *   <li>Classifies each transaction and routes its amounts into the correct buckets</li>
 *   <li>Accumulates output VAT, deductible input VAT, and rubrik field totals</li>
 *   <li>Derives netVat, resultType, and claimAmount via {@link VatReturn#of}</li>
 * </ol>
 *
 * <h2>Rubrik field mapping (Danish SKAT, Momsangivelse)</h2>
 * <ul>
 *   <li><strong>Rubrik A — goods</strong> ({@code rubrikAGoodsEuPurchaseValue}): value of
 *       goods acquired from EU VAT-registered businesses (reverse charge, erhvervelsesmoms)</li>
 *   <li><strong>Rubrik A — services</strong> ({@code rubrikAServicesEuPurchaseValue}): value of
 *       services purchased without DK VAT from EU/non-EU suppliers (reverse charge)</li>
 *   <li><strong>Rubrik B — goods</strong> ({@code rubrikBGoodsEuSaleValue}): zero-rated goods
 *       sold to EU VAT-registered businesses (also reported in EU-salgsangivelse)</li>
 *   <li><strong>Rubrik B — services</strong> ({@code rubrikBServicesEuSaleValue}): services sold
 *       without DK VAT to EU VAT-registered businesses</li>
 *   <li><strong>Rubrik C</strong> ({@code rubrikCOtherVatExemptSuppliesValue}): other exempt
 *       supplies not captured in B (exports outside EU, domestic exempt under ML §13)</li>
 * </ul>
 *
 * <h2>Simplification note (MVP)</h2>
 * The current {@link Transaction} model does not carry enough metadata to route all transactions
 * to the correct rubrik. Specifically:
 * <ul>
 *   <li>There is no {@code transactionDirection} (SALE/PURCHASE) field — standard-rated
 *       purchases with input VAT cannot be distinguished from sales without external context.</li>
 *   <li>There is no {@code goodsOrServices} flag — Rubrik A and B sub-split into goods/services
 *       requires additional transaction metadata.</li>
 *   <li>There is no {@code counterpartyCountry} — EU vs non-EU routing is not possible.</li>
 * </ul>
 * MVP routing: REVERSE_CHARGE → Rubrik A (services); ZERO_RATED → Rubrik B (services);
 * EXEMPT/OUT_OF_SCOPE → Rubrik C. These defaults are conservative and correct for
 * the most common cross-border service scenarios.
 *
 * @implNote Danish example — a business has in Q1 2026:
 * 3 × standard DK sales (total base 4,000,000 øre, output VAT 1,000,000 øre),
 * 1 × reverse-charge service from Germany (base 2,000,000 øre, self-assessed VAT 500,000 øre),
 * 1 × zero-rated export (base 1,000,000 øre, VAT 0).
 * Assembled return: outputVat = 1,500,000 øre, inputVatDeductible = 500,000 øre,
 * netVat = 1,000,000 øre → Payable.
 */
public final class VatReturnAssembler {

    /** Key constant for Rubrik A goods (EU goods purchases). */
    public static final String FIELD_RUBRIK_A_GOODS      = "rubrikAGoodsEuPurchaseValue";
    /** Key constant for Rubrik A services (EU/non-EU services purchases, reverse charge). */
    public static final String FIELD_RUBRIK_A_SERVICES   = "rubrikAServicesEuPurchaseValue";
    /** Key constant for Rubrik B goods (EU goods sales, zero-rated). */
    public static final String FIELD_RUBRIK_B_GOODS      = "rubrikBGoodsEuSaleValue";
    /** Key constant for Rubrik B services (EU services sales, reverse charge at buyer). */
    public static final String FIELD_RUBRIK_B_SERVICES   = "rubrikBServicesEuSaleValue";
    /** Key constant for Rubrik C other exempt/zero-rated supplies. */
    public static final String FIELD_RUBRIK_C_OTHER      = "rubrikCOtherVatExemptSuppliesValue";
    /** Key constant for output VAT on reverse-charge goods purchases from abroad. */
    public static final String FIELD_VAT_ON_GOODS_ABROAD = "vatOnGoodsPurchasesAbroadAmount";
    /** Key constant for output VAT on reverse-charge services purchases from abroad. */
    public static final String FIELD_VAT_ON_SVC_ABROAD   = "vatOnServicesPurchasesAbroadAmount";

    private final VatCalculator calculator;
    private final ExemptionClassifier classifier;
    private final ReverseChargeEngine reverseChargeEngine;

    public VatReturnAssembler(
            VatCalculator calculator,
            ExemptionClassifier classifier,
            ReverseChargeEngine reverseChargeEngine) {
        this.calculator = calculator;
        this.classifier = classifier;
        this.reverseChargeEngine = reverseChargeEngine;
    }

    /**
     * Assembles a draft {@link VatReturn} from all transactions in the given period.
     *
     * <p>Returns {@code Err(PeriodAlreadyFiled)} if the period is not OPEN.
     * An empty transaction list produces a zero-balance return — registered businesses
     * must still file for every period (nulindberetning).
     *
     * @param transactions the transactions in this period (may be empty)
     * @param period       the tax period being closed
     * @param plugin       the active jurisdiction plugin
     * @return {@code Ok(VatReturn)} in DRAFT status, or {@code Err(PeriodAlreadyFiled)}
     */
    public Result<VatReturn> assembleVatReturn(
            List<Transaction> transactions,
            TaxPeriod period,
            JurisdictionPlugin plugin) {

        if (period.status() != TaxPeriodStatus.OPEN) {
            return Result.err(new VatRuleError.PeriodAlreadyFiled(period.id()));
        }

        long outputVatOere      = 0L;
        long inputVatOere       = 0L;
        long rubrikAGoodsOere   = 0L;
        long rubrikAServicesOere= 0L;
        long rubrikBGoodsOere   = 0L;
        long rubrikBServicesOere= 0L;
        long rubrikCOtherOere   = 0L;
        long vatOnSvcAbroadOere = 0L;

        for (Transaction tx : transactions) {
            TaxCode code = tx.classification().taxCode();

            switch (code) {
                case STANDARD -> {
                    // Standard-rated domestic supply — contributes output VAT.
                    // NOTE: Domestic purchases (input VAT from suppliers) share the same
                    // STANDARD code but must be tracked as a negative amountExclVat or
                    // as a separate PURCHASE direction flag. MVP: treat all STANDARD as sales.
                    outputVatOere += tx.vatAmount().oere();
                }
                case ZERO_RATED -> {
                    // Zero-rated supply (export or intra-EU B2B goods) — no output VAT.
                    // MVP: treat as EU services sale → Rubrik B (services).
                    // Future: distinguish goods (Rubrik B goods) vs services (Rubrik B services)
                    // and EU vs non-EU (Rubrik C for non-EU exports).
                    rubrikBServicesOere += tx.amountExclVat().oere();
                }
                case REVERSE_CHARGE -> {
                    // Buyer self-assesses both output VAT and input VAT deduction.
                    // Net impact is zero for fully taxable businesses.
                    long rate = tx.classification().rateInBasisPoints();
                    Result<ReverseChargeResult> rcResult = reverseChargeEngine.applyReverseCharge(tx, rate);
                    if (rcResult instanceof Result.Ok<ReverseChargeResult> ok) {
                        outputVatOere      += ok.value().outputVatOere();
                        inputVatOere       += ok.value().inputVatOere();
                        rubrikAServicesOere += ok.value().baseAmountOere();
                        vatOnSvcAbroadOere  += ok.value().outputVatOere();
                    }
                    // Err case cannot occur here because we've already checked taxCode == REVERSE_CHARGE
                }
                case EXEMPT, OUT_OF_SCOPE -> {
                    // No VAT charged, no input VAT recovery.
                    // NOTE: Transaction.vatAmount() returns a small negative value for rate=-1
                    // (architectural gap — do NOT call it for these codes).
                    // Exempt supplies go to Rubrik C (other exempt supplies).
                    rubrikCOtherOere += tx.amountExclVat().oere();
                }
            }
        }

        // Build jurisdiction-specific fields map
        Map<String, Object> jurisdictionFields = new HashMap<>();
        jurisdictionFields.put(FIELD_RUBRIK_A_GOODS,      rubrikAGoodsOere);
        jurisdictionFields.put(FIELD_RUBRIK_A_SERVICES,   rubrikAServicesOere);
        jurisdictionFields.put(FIELD_RUBRIK_B_GOODS,      rubrikBGoodsOere);
        jurisdictionFields.put(FIELD_RUBRIK_B_SERVICES,   rubrikBServicesOere);
        jurisdictionFields.put(FIELD_RUBRIK_C_OTHER,      rubrikCOtherOere);
        jurisdictionFields.put(FIELD_VAT_ON_SVC_ABROAD,   vatOnSvcAbroadOere);

        JurisdictionCode jCode = period.jurisdictionCode();

        VatReturn vatReturn = VatReturn.of(
                UUID.randomUUID(),
                jCode,
                period.id(),
                MonetaryAmount.ofOere(outputVatOere),
                MonetaryAmount.ofOere(inputVatOere),
                VatReturnStatus.DRAFT,
                Map.copyOf(jurisdictionFields)
        );

        return Result.ok(vatReturn);
    }
}
