package com.netcompany.vat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MonetaryAmount}.
 *
 * <p>Risk R17 (implementation risk register): ALL monetary arithmetic must use exact
 * integer operations in øre. Never float or double. Tests here verify this property
 * structurally by operating only on the {@code long} backing field.
 */
@DisplayName("MonetaryAmount")
class MonetaryAmountTest {

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ofOere factory constructs from øre value")
    void ofOere_constructsFromLong() {
        MonetaryAmount amount = MonetaryAmount.ofOere(25_000L);
        assertEquals(25_000L, amount.oere());
    }

    @Test
    @DisplayName("ZERO constant is exactly zero øre")
    void zero_constant_isZero() {
        assertEquals(0L, MonetaryAmount.ZERO.oere());
        assertTrue(MonetaryAmount.ZERO.isZero());
    }

    @Test
    @DisplayName("zero() factory returns zero amount")
    void zero_factory_returnsZero() {
        MonetaryAmount z = MonetaryAmount.zero();
        assertEquals(0L, z.oere());
    }

    // ── Negative amounts (credit notes) ───────────────────────────────────────

    @Test
    @DisplayName("negative øre value is valid (represents credit note or refund)")
    void negative_amount_isValid() {
        MonetaryAmount credit = MonetaryAmount.ofOere(-50_000L);
        assertEquals(-50_000L, credit.oere());
        assertTrue(credit.isNegative());
        assertFalse(credit.isPositive());
        assertFalse(credit.isZero());
    }

    // ── add() ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add two positive amounts")
    void add_twoPositiveAmounts() {
        MonetaryAmount a = MonetaryAmount.ofOere(100_000L);
        MonetaryAmount b = MonetaryAmount.ofOere(50_000L);
        assertEquals(150_000L, a.add(b).oere());
    }

    @Test
    @DisplayName("add with zero returns original value")
    void add_withZero_returnsOriginal() {
        MonetaryAmount a = MonetaryAmount.ofOere(75_000L);
        assertEquals(75_000L, a.add(MonetaryAmount.ZERO).oere());
    }

    @Test
    @DisplayName("add negative amount (credit against invoice)")
    void add_negativeAmount() {
        MonetaryAmount invoice = MonetaryAmount.ofOere(100_000L);
        MonetaryAmount credit  = MonetaryAmount.ofOere(-30_000L);
        assertEquals(70_000L, invoice.add(credit).oere());
    }

    @Test
    @DisplayName("add is commutative: a + b == b + a")
    void add_isCommutative() {
        MonetaryAmount a = MonetaryAmount.ofOere(12_345L);
        MonetaryAmount b = MonetaryAmount.ofOere(67_890L);
        assertEquals(a.add(b).oere(), b.add(a).oere());
    }

    // ── subtract() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("subtract produces expected difference")
    void subtract_positiveDifference() {
        MonetaryAmount a = MonetaryAmount.ofOere(500_000L);
        MonetaryAmount b = MonetaryAmount.ofOere(200_000L);
        assertEquals(300_000L, a.subtract(b).oere());
    }

    @Test
    @DisplayName("subtract same amount produces zero")
    void subtract_sameAmount_producesZero() {
        MonetaryAmount a = MonetaryAmount.ofOere(100_000L);
        assertEquals(0L, a.subtract(a).oere());
    }

    @Test
    @DisplayName("subtract larger amount produces negative (claimable scenario)")
    void subtract_largerAmount_producesNegative() {
        MonetaryAmount output = MonetaryAmount.ofOere(50_000L);
        MonetaryAmount input  = MonetaryAmount.ofOere(100_000L);
        MonetaryAmount net = output.subtract(input);
        assertEquals(-50_000L, net.oere());
        assertTrue(net.isNegative());
    }

    // ── negate() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("negate positive amount produces negative")
    void negate_positiveAmount() {
        MonetaryAmount pos = MonetaryAmount.ofOere(25_000L);
        assertEquals(-25_000L, pos.negate().oere());
    }

    @Test
    @DisplayName("negate negative amount produces positive")
    void negate_negativeAmount() {
        MonetaryAmount neg = MonetaryAmount.ofOere(-25_000L);
        assertEquals(25_000L, neg.negate().oere());
    }

    @Test
    @DisplayName("negate zero produces zero")
    void negate_zero_producesZero() {
        assertEquals(0L, MonetaryAmount.ZERO.negate().oere());
    }

    @Test
    @DisplayName("double negation returns original value")
    void negate_doubleNegation_isIdentity() {
        MonetaryAmount original = MonetaryAmount.ofOere(123_456L);
        assertEquals(original.oere(), original.negate().negate().oere());
    }

    // ── isPositive / isNegative / isZero ─────────────────────────────────────

    @Test
    @DisplayName("isPositive returns true for positive, false otherwise")
    void isPositive_returnsCorrectly() {
        assertTrue(MonetaryAmount.ofOere(1L).isPositive());
        assertFalse(MonetaryAmount.ofOere(0L).isPositive());
        assertFalse(MonetaryAmount.ofOere(-1L).isPositive());
    }

    @Test
    @DisplayName("isNegative returns true for negative, false otherwise")
    void isNegative_returnsCorrectly() {
        assertTrue(MonetaryAmount.ofOere(-1L).isNegative());
        assertFalse(MonetaryAmount.ofOere(0L).isNegative());
        assertFalse(MonetaryAmount.ofOere(1L).isNegative());
    }

    @Test
    @DisplayName("isZero returns true only for zero")
    void isZero_returnsCorrectly() {
        assertTrue(MonetaryAmount.ofOere(0L).isZero());
        assertFalse(MonetaryAmount.ofOere(1L).isZero());
        assertFalse(MonetaryAmount.ofOere(-1L).isZero());
    }

    // ── toString() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString formats as DKK with two decimal places")
    void toString_formatsAsDkk() {
        // 2500 øre = 25.00 DKK
        assertEquals("25.00 DKK", MonetaryAmount.ofOere(2500L).toString());
    }

    @Test
    @DisplayName("toString handles zero øre correctly")
    void toString_handlesZero() {
        assertEquals("0.00 DKK", MonetaryAmount.ZERO.toString());
    }

    @Test
    @DisplayName("toString handles sub-krone amounts (øre only)")
    void toString_handlesSubKrone() {
        // 75 øre = 0.75 DKK
        assertEquals("0.75 DKK", MonetaryAmount.ofOere(75L).toString());
    }

    @Test
    @DisplayName("toString handles negative amounts (credit note representation)")
    void toString_handlesNegative() {
        // -5000 øre = -50.00 DKK → sign is on the whole part
        MonetaryAmount neg = MonetaryAmount.ofOere(-5_000L);
        String str = neg.toString();
        // The whole part is negative; fraction uses absolute value
        assertTrue(str.contains("-50"), "Expected negative DKK representation, got: " + str);
        assertTrue(str.endsWith("DKK"), "Expected DKK suffix, got: " + str);
    }

    // ── Large amounts — Risk R3 (monetary precision) ──────────────────────────

    @Test
    @DisplayName("R3: billion-øre amounts do not overflow (large enterprise scenario)")
    void largeAmount_doesNotOverflow() {
        // 10,000,000,000 DKK = 1,000,000,000,000 øre — well within long range (max ~9.2×10^18)
        long bigOere = 1_000_000_000_000L; // 10 billion DKK
        MonetaryAmount large = MonetaryAmount.ofOere(bigOere);
        assertEquals(bigOere, large.oere());

        // Adding two large amounts
        MonetaryAmount doubled = large.add(large);
        assertEquals(bigOere * 2, doubled.oere());
    }

    @Test
    @DisplayName("R3: VAT on maximum practical invoice (1 billion DKK, 25%) is exact")
    void maxPracticalInvoice_vatIsExact() {
        // 1,000,000,000 DKK = 100,000,000,000 øre → 25% = 25,000,000,000 øre
        long baseOere = 100_000_000_000L;
        long vatOere  = baseOere * 2500L / 10_000L;
        MonetaryAmount vat = MonetaryAmount.ofOere(vatOere);
        assertEquals(25_000_000_000L, vat.oere());
    }

    // ── Immutability ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("add does not mutate either operand")
    void add_doesNotMutate() {
        MonetaryAmount a = MonetaryAmount.ofOere(100L);
        MonetaryAmount b = MonetaryAmount.ofOere(200L);
        a.add(b);
        assertEquals(100L, a.oere(), "add must not mutate the receiver");
        assertEquals(200L, b.oere(), "add must not mutate the argument");
    }

    @Test
    @DisplayName("subtract does not mutate either operand")
    void subtract_doesNotMutate() {
        MonetaryAmount a = MonetaryAmount.ofOere(500L);
        MonetaryAmount b = MonetaryAmount.ofOere(200L);
        a.subtract(b);
        assertEquals(500L, a.oere(), "subtract must not mutate the receiver");
        assertEquals(200L, b.oere(), "subtract must not mutate the argument");
    }
}
