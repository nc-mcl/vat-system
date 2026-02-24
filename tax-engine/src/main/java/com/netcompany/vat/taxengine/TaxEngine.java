package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

import java.time.LocalDate;
import java.util.List;

/**
 * High-level façade that composes all tax-engine components into a single
 * jurisdiction-bound entry point.
 *
 * <p>Instantiated with a {@link JurisdictionPlugin} at startup; all method calls
 * delegate to the plugin-parameterised components. This facade is jurisdiction-agnostic —
 * it works identically for DK, NO, DE, or any future plugin.
 *
 * <p>Typical usage:
 * <pre>{@code
 * TaxEngine engine = new TaxEngine(new DkJurisdictionPlugin());
 *
 * // Classify a transaction
 * Result<TaxClassification> cls = engine.classifyTransaction(tx);
 *
 * // Assemble a VAT return from the period's transactions
 * Result<VatReturn> vatReturn = engine.assembleReturn(transactions, period);
 *
 * // Calculate VAT for an ad-hoc amount
 * Result<Long> vat = engine.calculateVat(amountOere, TaxCode.STANDARD, LocalDate.now());
 * }</pre>
 *
 * @implNote All internal components are pure functions — no I/O, no state mutation,
 * no randomness. The same inputs always produce the same outputs regardless of when
 * or how many times the engine is called.
 */
public final class TaxEngine {

    private final JurisdictionPlugin plugin;
    private final RateResolver rateResolver;
    private final VatCalculator calculator;
    private final ReverseChargeEngine reverseChargeEngine;
    private final ExemptionClassifier exemptionClassifier;
    private final FilingPeriodCalculator filingPeriodCalculator;
    private final VatReturnAssembler vatReturnAssembler;

    /**
     * Constructs a TaxEngine bound to the given jurisdiction plugin.
     *
     * @param plugin the jurisdiction plugin that provides all country-specific rules
     */
    public TaxEngine(JurisdictionPlugin plugin) {
        this.plugin = plugin;
        this.rateResolver = new RateResolver();
        this.calculator = new VatCalculator();
        this.reverseChargeEngine = new ReverseChargeEngine(calculator);
        this.exemptionClassifier = new ExemptionClassifier(rateResolver);
        this.filingPeriodCalculator = new FilingPeriodCalculator();
        this.vatReturnAssembler = new VatReturnAssembler(calculator, exemptionClassifier, reverseChargeEngine);
    }

    /**
     * Assembles a draft VAT return from all transactions in the given period.
     *
     * @param transactions the transactions for the period (may be empty)
     * @param period       the tax period being closed
     * @return {@code Ok(VatReturn)} in DRAFT status, or {@code Err(PeriodAlreadyFiled)}
     * @see VatReturnAssembler#assembleVatReturn
     */
    public Result<VatReturn> assembleReturn(List<Transaction> transactions, TaxPeriod period) {
        return vatReturnAssembler.assembleVatReturn(transactions, period, plugin);
    }

    /**
     * Validates and confirms the VAT classification for a transaction.
     *
     * @param transaction the transaction to classify
     * @return {@code Ok(TaxClassification)} with plugin-confirmed rate,
     *         {@code Err(UnknownTaxCode)} if the code is not supported
     * @see ExemptionClassifier#classifyTransaction
     */
    public Result<TaxClassification> classifyTransaction(Transaction transaction) {
        return exemptionClassifier.classifyTransaction(transaction, plugin);
    }

    /**
     * Calculates the VAT amount for an ad-hoc base amount and tax code.
     *
     * <p>Returns {@code Ok(0)} for EXEMPT and OUT_OF_SCOPE codes (rate = −1).
     * Returns {@code Err(UnknownTaxCode)} if the plugin does not support the code.
     *
     * @param amountExclVatOere the base taxable amount in øre
     * @param taxCode           the VAT treatment to apply
     * @param effectiveDate     the date on which the rate must be valid
     * @return the computed VAT amount in øre, or an error
     */
    public Result<Long> calculateVat(long amountExclVatOere, TaxCode taxCode, LocalDate effectiveDate) {
        return rateResolver
                .resolveVatRate(plugin, taxCode, effectiveDate)
                .map(rate -> rate < 0 ? 0L : calculator.calculateOutputVat(amountExclVatOere, rate));
    }

    /**
     * Returns the jurisdiction plugin this engine is bound to.
     * Useful for inspecting jurisdiction metadata (authority name, ViDA flag, etc.).
     *
     * @return the active jurisdiction plugin
     */
    public JurisdictionPlugin getPlugin() {
        return plugin;
    }
}
