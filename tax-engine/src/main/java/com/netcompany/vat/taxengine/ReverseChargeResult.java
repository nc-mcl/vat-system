package com.netcompany.vat.taxengine;

/**
 * The output of applying the reverse charge mechanism to a single transaction.
 *
 * <p>Under reverse charge (omvendt betalingspligt), the buyer self-assesses VAT:
 * <ol>
 *   <li>The buyer declares <em>output VAT</em> as if they had charged it (increases tax liability).</li>
 *   <li>The buyer simultaneously claims the same amount as <em>input VAT deduction</em>
 *       (offsets tax liability), subject to the buyer's deduction entitlement.</li>
 * </ol>
 *
 * <p>For a fully VAT-registered business with 100% taxable activity, the net impact
 * on VAT payable is zero — the output and input cancel each other out. However, the
 * gross amounts must still be reported on the VAT return in the appropriate rubrik fields.
 *
 * @param outputVatOere        the self-assessed output VAT amount, in øre (always ≥ 0)
 * @param inputVatOere         the corresponding input VAT deduction, in øre (always ≥ 0).
 *                             For a fully deductible business this equals {@code outputVatOere}.
 * @param rateInBasisPoints    the rate that was applied (e.g. 2500 = 25.00%)
 * @param baseAmountOere       the taxable base amount (excl. VAT), in øre
 *
 * @implNote Danish example — a business buys IT consulting services from a German supplier
 * for 100,000 DKK (10,000,000 øre). The DK buyer applies reverse charge at 25%:
 * {@code outputVat = 2,500,000 øre}, {@code inputVat = 2,500,000 øre}, net = 0 øre.
 * The 10,000,000 øre base is reported in Rubrik A (services).
 */
public record ReverseChargeResult(
        long outputVatOere,
        long inputVatOere,
        long rateInBasisPoints,
        long baseAmountOere
) {

    public ReverseChargeResult {
        if (outputVatOere < 0) {
            throw new IllegalArgumentException("outputVatOere must be non-negative, got: " + outputVatOere);
        }
        if (inputVatOere < 0) {
            throw new IllegalArgumentException("inputVatOere must be non-negative, got: " + inputVatOere);
        }
        if (rateInBasisPoints < 0) {
            throw new IllegalArgumentException("rateInBasisPoints must be non-negative, got: " + rateInBasisPoints);
        }
    }

    /**
     * Returns the net VAT impact of this reverse charge transaction.
     * For a fully deductible business this is always zero.
     *
     * @return {@code outputVatOere - inputVatOere}
     */
    public long netVatImpactOere() {
        return outputVatOere - inputVatOere;
    }
}
