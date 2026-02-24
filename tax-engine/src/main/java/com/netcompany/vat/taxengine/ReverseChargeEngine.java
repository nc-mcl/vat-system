package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatRuleError;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

/**
 * Evaluates and applies the reverse charge mechanism (omvendt betalingspligt).
 *
 * <p>Reverse charge shifts VAT accounting from the supplier to the buyer.
 * The buyer simultaneously declares output VAT (self-assessed) and claims input VAT
 * deduction. For a fully VAT-registered business with 100% taxable activity, the net
 * VAT impact is zero — but both amounts must be reported on the VAT return.
 *
 * <h2>When reverse charge applies in Denmark</h2>
 * <ul>
 *   <li>Cross-border B2B services purchased from non-Danish suppliers (general B2B rule)</li>
 *   <li>EU intra-community acquisitions of goods — acquisition VAT (erhvervelsesmoms)</li>
 *   <li>Domestic supplies listed in ML §46 (CO₂ credits, investment gold, metal scrap,
 *       mobile phones, integrated circuits, games consoles, gas/electricity to distributors)</li>
 * </ul>
 *
 * <p>This engine defers all jurisdiction-specific applicability rules to the
 * {@link JurisdictionPlugin}. The engine itself contains no hardcoded country logic.
 *
 * @implNote Danish example — a Danish business buys 80,000 DKK of software consultancy
 * from a German supplier. The German supplier invoices without Danish VAT (8,000,000 øre base).
 * The Danish buyer applies reverse charge at 25%: {@code outputVatOere = 2,000,000} and
 * {@code inputVatOere = 2,000,000} (fully deductible). The base is reported in Rubrik A (services).
 */
public final class ReverseChargeEngine {

    private final VatCalculator calculator;

    public ReverseChargeEngine(VatCalculator calculator) {
        this.calculator = calculator;
    }

    /**
     * Determines whether reverse charge applies to the given transaction.
     *
     * <p>Delegates entirely to the jurisdiction plugin. The plugin checks whether
     * the transaction's classification has {@code taxCode == REVERSE_CHARGE}.
     *
     * @param transaction the transaction to evaluate
     * @param plugin      the active jurisdiction plugin
     * @return {@code Ok(true)} if reverse charge applies, {@code Ok(false)} otherwise.
     *         Never returns an {@code Err} — invalid classification errors are caught
     *         during classification, not here.
     */
    public Result<Boolean> isReverseChargeApplicable(Transaction transaction, JurisdictionPlugin plugin) {
        return Result.ok(plugin.isReverseChargeApplicable(transaction));
    }

    /**
     * Calculates the reverse charge VAT amounts for a single transaction.
     *
     * <p>Under reverse charge, the buyer self-assesses:
     * <ul>
     *   <li><strong>Output VAT</strong> = {@code baseAmount × rate / 10_000} (adds to tax liability)</li>
     *   <li><strong>Input VAT</strong> = same amount (deducted immediately for fully taxable businesses)</li>
     * </ul>
     *
     * <p>The input VAT deduction is set equal to the output VAT, assuming the buyer has
     * 100% taxable activity and full deduction entitlement. For businesses with partial
     * deduction rights, the caller must apply a pro-rata adjustment to {@link ReverseChargeResult#inputVatOere()}.
     *
     * @param transaction        the reverse-charge transaction (must have REVERSE_CHARGE tax code)
     * @param rateInBasisPoints  the rate to apply (e.g. 2500 = 25.00%)
     * @return {@code Ok(ReverseChargeResult)} or {@code Err(InvalidTransaction)} if the
     *         transaction is not classified as REVERSE_CHARGE
     */
    public Result<ReverseChargeResult> applyReverseCharge(Transaction transaction, long rateInBasisPoints) {
        if (transaction.classification().taxCode() != TaxCode.REVERSE_CHARGE) {
            return Result.err(new VatRuleError.InvalidTransaction(
                    "Transaction " + transaction.id() + " is not classified as REVERSE_CHARGE; " +
                    "actual: " + transaction.classification().taxCode()));
        }

        long baseOere = transaction.amountExclVat().oere();
        long outputVat = calculator.calculateOutputVat(baseOere, rateInBasisPoints);
        // For a fully deductible business, input VAT equals output VAT.
        // Partial deduction adjustments are applied externally by the caller.
        long inputVat = outputVat;

        return Result.ok(new ReverseChargeResult(outputVat, inputVat, rateInBasisPoints, baseOere));
    }

    /**
     * Validates that a counterparty has the VAT number required for reverse charge processing.
     *
     * <p>Reverse charge can only apply when the counterparty is a VAT-registered business.
     * Suppliers invoicing under reverse charge must include the buyer's VAT registration
     * number on the invoice (SKAT invoice requirements, ML §52a).
     *
     * @param counterparty the counterparty to validate
     * @return {@code Ok(counterparty)} if a non-blank VAT number is present,
     *         {@code Err(MissingVatNumber)} otherwise
     */
    public Result<Counterparty> validateCounterpartyVatNumber(Counterparty counterparty) {
        if (counterparty.vatNumber() == null || counterparty.vatNumber().isBlank()) {
            return Result.err(new VatRuleError.MissingVatNumber(counterparty.id().toString()));
        }
        return Result.ok(counterparty);
    }
}
