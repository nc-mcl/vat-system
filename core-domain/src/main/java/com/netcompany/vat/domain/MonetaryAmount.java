package com.netcompany.vat.domain;

/**
 * Immutable monetary value stored as the smallest currency unit (øre for DKK).
 *
 * <p>Why {@code long} and not {@code BigDecimal}?
 * <ul>
 *   <li>Integer arithmetic is exact — no floating-point rounding errors</li>
 *   <li>Performance: {@code long} arithmetic is ~10× faster than {@code BigDecimal}</li>
 *   <li>VAT law requires precision to the øre (1/100 DKK); two decimal places is sufficient</li>
 * </ul>
 *
 * <p>Display conversion: 1 DKK = 100 øre. To format for display, divide by 100 and
 * show two decimal places. The {@link #toString()} method does this for diagnostic use.
 *
 * <p>Never use {@code double} or {@code float} for monetary amounts.
 */
public record MonetaryAmount(long oere) {

    /** The zero amount. */
    public static final MonetaryAmount ZERO = new MonetaryAmount(0L);

    /**
     * Constructs a monetary amount from the given øre value.
     *
     * @throws IllegalArgumentException never — any {@code long} is a valid øre amount
     */
    public MonetaryAmount {
        // Validation placeholder — domain rules may restrict negative amounts in specific contexts
    }

    /** Factory: constructs from øre. */
    public static MonetaryAmount ofOere(long oere) {
        return new MonetaryAmount(oere);
    }

    /** Factory: zero amount. */
    public static MonetaryAmount zero() {
        return ZERO;
    }

    /** Returns the sum of this amount and {@code other}. */
    public MonetaryAmount add(MonetaryAmount other) {
        return new MonetaryAmount(this.oere + other.oere);
    }

    /** Returns this amount minus {@code other}. */
    public MonetaryAmount subtract(MonetaryAmount other) {
        return new MonetaryAmount(this.oere - other.oere);
    }

    /** Returns the additive inverse (negation) of this amount. */
    public MonetaryAmount negate() {
        return new MonetaryAmount(-this.oere);
    }

    /** Returns true if this amount is greater than zero. */
    public boolean isPositive() {
        return this.oere > 0;
    }

    /** Returns true if this amount is less than zero. */
    public boolean isNegative() {
        return this.oere < 0;
    }

    /** Returns true if this amount equals zero. */
    public boolean isZero() {
        return this.oere == 0;
    }

    /**
     * Formats for display as DKK with two decimal places.
     * For example, 2500 øre → "25.00 DKK".
     *
     * <p>This is for diagnostic / logging use only. Do not parse this output.
     */
    @Override
    public String toString() {
        long whole = oere / 100;
        long fraction = Math.abs(oere % 100);
        return String.format("%d.%02d DKK", whole, fraction);
    }
}
