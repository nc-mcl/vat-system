package com.netcompany.vat.taxengine;

/**
 * The result of a net VAT calculation for a filing period.
 *
 * <p>Sealed discriminated union — exhaustively pattern-match in switch expressions:
 * <pre>{@code
 * switch (result) {
 *     case VatResult.Payable p  -> schedulePayment(p.amount());
 *     case VatResult.Claimable c -> submitRefundClaim(c.amount());
 *     case VatResult.Zero z      -> fileZeroReturn();
 * }
 * }</pre>
 *
 * <p>All amounts are in the smallest currency unit (øre for DKK).
 *
 * @implNote Danish example — a business with 200,000 øre output VAT and 75,000 øre
 * deductible input VAT has a net of 125,000 øre → {@code Payable(125_000)}.
 * A business with 50,000 øre output and 80,000 øre input has net −30,000 → {@code Claimable(30_000)}.
 */
public sealed interface VatResult permits VatResult.Payable, VatResult.Claimable, VatResult.Zero {

    /**
     * Net VAT is positive — the taxpayer owes this amount to the tax authority.
     *
     * @param amount the positive amount owed, in øre; always > 0
     */
    record Payable(long amount) implements VatResult {
        public Payable {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "Payable amount must be positive, got: " + amount);
            }
        }
    }

    /**
     * Net VAT is negative — the taxpayer may reclaim this amount from the tax authority.
     *
     * @param amount the positive refund amount, in øre; always > 0
     */
    record Claimable(long amount) implements VatResult {
        public Claimable {
            if (amount <= 0) {
                throw new IllegalArgumentException(
                        "Claimable amount must be positive, got: " + amount);
            }
        }
    }

    /**
     * Net VAT is exactly zero — a nil return is filed.
     *
     * @implNote Danish: nulindberetning — a registered business must still file
     * a return for every period even when the result is zero.
     */
    record Zero() implements VatResult {}

    /** Singleton zero instance — use instead of {@code new Zero()} for efficiency. */
    Zero ZERO = new Zero();
}
