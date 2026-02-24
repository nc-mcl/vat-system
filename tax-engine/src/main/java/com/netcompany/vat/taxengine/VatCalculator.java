package com.netcompany.vat.taxengine;

/**
 * Performs exact integer arithmetic for VAT calculations.
 *
 * <p>All amounts are in øre (1/100 DKK). All rates are in basis points (2500 = 25.00%).
 * Integer division always truncates toward zero — rounding is always in favour of the
 * taxpayer, never rounded up. This is consistent with Danish SKAT convention and
 * avoids accumulated rounding errors across many transactions.
 *
 * <p><strong>No floating-point arithmetic is used anywhere in this class.</strong>
 * The formula {@code amount * rate / 10_000} is computed using {@code long} arithmetic.
 * At the maximum representable amount (≈ 9.2 × 10¹⁸ øre), overflow would occur before
 * dividing. In practice DK VAT amounts fit well within {@code long} range.
 *
 * @implNote Danish examples:
 * <ul>
 *   <li>Invoice 100,000 DKK = 10,000,000 øre excl. VAT at 25% (2500 bp):
 *       output VAT = 10,000,000 × 2500 / 10,000 = 2,500,000 øre = 25,000 DKK</li>
 *   <li>Mixed-use input VAT 500,000 øre with 70% deduction (7000 bp):
 *       deductible = 500,000 × 7000 / 10,000 = 350,000 øre</li>
 * </ul>
 */
public final class VatCalculator {

    /**
     * Calculates output VAT for a transaction.
     *
     * <p>Formula: {@code amountExclVat × rateInBasisPoints / 10_000}
     * (integer division, truncates toward zero).
     *
     * <p>Must not be called for EXEMPT or OUT_OF_SCOPE transactions
     * (where the rate is −1). Callers must guard against −1 rates before invoking this.
     *
     * @param amountExclVat      the taxable base amount in øre (must be ≥ 0)
     * @param rateInBasisPoints  the VAT rate in basis points (e.g. 2500 = 25.00%)
     * @return output VAT in øre, truncated toward zero
     */
    public long calculateOutputVat(long amountExclVat, long rateInBasisPoints) {
        return amountExclVat * rateInBasisPoints / 10_000L;
    }

    /**
     * Calculates the deductible portion of input VAT for mixed-use purchases.
     *
     * <p>When a business uses inputs for both taxable and exempt activities, only the
     * proportion attributable to taxable activities is deductible (ML §38, pro-rata rule).
     *
     * <p>Formula: {@code inputVat × deductionRateBasisPoints / 10_000}
     * (integer division, truncates toward zero — in favour of the taxpayer).
     *
     * <p>For fully deductible businesses (100% taxable activity), pass 10_000 as
     * the deduction rate to recover the full input VAT amount.
     *
     * @param inputVat                  the gross input VAT amount in øre
     * @param deductionRateBasisPoints  the pro-rata deduction entitlement in basis points
     *                                  (10_000 = 100% fully deductible, 7000 = 70%, etc.)
     * @return the deductible portion of input VAT in øre, truncated toward zero
     */
    public long calculateInputVatDeduction(long inputVat, long deductionRateBasisPoints) {
        return inputVat * deductionRateBasisPoints / 10_000L;
    }

    /**
     * Calculates net VAT for the period.
     *
     * <p>Formula: {@code outputVat − deductibleInputVat}.
     * A positive result means the taxpayer owes VAT.
     * A negative result means the taxpayer is owed a refund.
     *
     * @param outputVat           total output VAT collected in øre
     * @param deductibleInputVat  total deductible input VAT in øre
     * @return net VAT in øre (positive = payable, negative = claimable refund)
     */
    public long calculateNetVat(long outputVat, long deductibleInputVat) {
        return outputVat - deductibleInputVat;
    }

    /**
     * Determines whether net VAT results in a payment obligation, a refund claim, or zero.
     *
     * @param netVat net VAT amount in øre (may be positive, negative, or zero)
     * @return {@link VatResult.Payable} if {@code netVat > 0},
     *         {@link VatResult.Claimable} if {@code netVat < 0} (amount is absolute value),
     *         {@link VatResult.Zero} if {@code netVat == 0}
     */
    public VatResult determineResult(long netVat) {
        if (netVat > 0) {
            return new VatResult.Payable(netVat);
        } else if (netVat < 0) {
            return new VatResult.Claimable(-netVat);
        } else {
            return new VatResult.Zero();
        }
    }
}
