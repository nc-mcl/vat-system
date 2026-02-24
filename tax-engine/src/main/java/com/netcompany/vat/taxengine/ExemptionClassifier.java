package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatRuleError;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

/**
 * Classifies a transaction's VAT treatment by cross-checking the stored tax code
 * against the jurisdiction plugin's authoritative rate schedule.
 *
 * <p>The classifier confirms that the tax code on the transaction is supported by the
 * active jurisdiction, and returns a {@link TaxClassification} whose rate is populated
 * from the plugin. This guards against stale rates if the plugin's rate schedule has
 * changed since the transaction was created.
 *
 * <h2>Danish VAT classification categories (ML §§4, 13, 38, 46)</h2>
 * <ul>
 *   <li><strong>STANDARD</strong> — 25% MOMS on domestic taxable goods and services</li>
 *   <li><strong>ZERO_RATED</strong> — 0% on exports, intra-EU B2B goods, international transport</li>
 *   <li><strong>EXEMPT</strong> — no VAT, no input recovery: healthcare (ML §13(1)(1)),
 *       education (ML §13(1)(3)), financial services (ML §13(1)(11)),
 *       real property rental/sale (ML §13(1)(9)), passenger transport (ML §13(1)(15))</li>
 *   <li><strong>REVERSE_CHARGE</strong> — buyer self-assesses: cross-border B2B services,
 *       EU goods acquisitions, domestic ML §46 categories</li>
 *   <li><strong>OUT_OF_SCOPE</strong> — not subject to VAT: wages, dividends, equity transfers</li>
 * </ul>
 *
 * @implNote Danish example — a transaction for medical services is classified as EXEMPT.
 * The classifier verifies the plugin returns −1 (not applicable) for EXEMPT, then returns
 * {@code Ok(TaxClassification(EXEMPT, -1, false, effectiveDate))}. No VAT is calculated.
 */
public final class ExemptionClassifier {

    private final RateResolver rateResolver;

    public ExemptionClassifier(RateResolver rateResolver) {
        this.rateResolver = rateResolver;
    }

    /**
     * Validates and confirms the transaction's VAT classification against the jurisdiction plugin.
     *
     * <p>Returns the confirmed {@link TaxClassification} with the rate populated from the plugin
     * for the transaction's effective date. Returns {@code Err(UnknownTaxCode)} if the plugin
     * does not support the transaction's tax code.
     *
     * <p>For EXEMPT and OUT_OF_SCOPE codes, the rate is −1 (not applicable). No VAT amount
     * should be computed for these classifications.
     *
     * <p>For REVERSE_CHARGE, the returned classification has {@code isReverseCharge = true}
     * and the standard self-assessment rate (e.g. 2500 for Denmark).
     *
     * @param transaction the transaction to classify
     * @param plugin      the active jurisdiction plugin
     * @return {@code Ok(TaxClassification)} with plugin-confirmed rate,
     *         {@code Err(UnknownTaxCode)} if the code is not supported by the plugin
     */
    public Result<TaxClassification> classifyTransaction(Transaction transaction, JurisdictionPlugin plugin) {
        TaxCode taxCode = transaction.classification().taxCode();

        Result<Long> rateResult = rateResolver.resolveVatRate(plugin, taxCode, transaction.transactionDate());

        return rateResult.map(rate -> new TaxClassification(
                taxCode,
                rate,
                taxCode == TaxCode.REVERSE_CHARGE,
                transaction.transactionDate()
        ));
    }
}
