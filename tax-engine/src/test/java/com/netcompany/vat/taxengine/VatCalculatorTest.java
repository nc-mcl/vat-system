package com.netcompany.vat.taxengine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VatCalculator}.
 *
 * <p>Verifies exact integer arithmetic, truncation behaviour, and result classification.
 * All amounts in øre (1/100 DKK); all rates in basis points (2500 = 25.00%).
 */
@DisplayName("VatCalculator")
class VatCalculatorTest {

    private VatCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new VatCalculator();
    }

    // --- calculateOutputVat ---

    @Test
    @DisplayName("25% on 10,000 DKK = 2,500 DKK output VAT")
    void calculateOutputVat_standardRate() {
        // 10,000 DKK = 1,000,000 øre; 25% rate = 2500 bp
        long vat = calculator.calculateOutputVat(1_000_000L, 2500L);
        assertEquals(250_000L, vat); // 2,500 DKK
    }

    @Test
    @DisplayName("0% rate produces zero output VAT")
    void calculateOutputVat_zeroRate() {
        long vat = calculator.calculateOutputVat(1_000_000L, 0L);
        assertEquals(0L, vat);
    }

    @Test
    @DisplayName("zero base amount produces zero VAT regardless of rate")
    void calculateOutputVat_zeroBase() {
        assertEquals(0L, calculator.calculateOutputVat(0L, 2500L));
    }

    @Test
    @DisplayName("truncates toward zero — never rounds up (in favour of taxpayer)")
    void calculateOutputVat_truncatesNotRoundsUp() {
        // 1 øre at 25% → 0.25 øre → should truncate to 0, not round to 1
        long vat = calculator.calculateOutputVat(1L, 2500L);
        assertEquals(0L, vat);

        // 3 øre at 25% → 0.75 øre → truncate to 0
        long vat2 = calculator.calculateOutputVat(3L, 2500L);
        assertEquals(0L, vat2);

        // 4 øre at 25% → 1.0 øre → exact, no truncation needed
        long vat3 = calculator.calculateOutputVat(4L, 2500L);
        assertEquals(1L, vat3);
    }

    @Test
    @DisplayName("large invoice (1M DKK) calculates correctly")
    void calculateOutputVat_largeAmount() {
        // 1,000,000 DKK = 100,000,000 øre; 25% VAT = 25,000,000 øre = 250,000 DKK
        long vat = calculator.calculateOutputVat(100_000_000L, 2500L);
        assertEquals(25_000_000L, vat);
    }

    // --- calculateInputVatDeduction ---

    @Test
    @DisplayName("100% deduction returns full input VAT")
    void calculateInputVatDeduction_fullDeduction() {
        // 10,000 bp = 100%
        long deductible = calculator.calculateInputVatDeduction(500_000L, 10_000L);
        assertEquals(500_000L, deductible);
    }

    @Test
    @DisplayName("25% pro-rata deduction (restaurant meals rule)")
    void calculateInputVatDeduction_restaurantMeals() {
        // Input VAT on restaurant bill: 100,000 øre; only 25% deductible
        // DK rule: 25% of the input VAT amount (not 25% of gross bill)
        long deductible = calculator.calculateInputVatDeduction(100_000L, 2500L);
        assertEquals(25_000L, deductible);
    }

    @Test
    @DisplayName("70% pro-rata for mixed-use business (70% taxable activity)")
    void calculateInputVatDeduction_proRata70Percent() {
        // Input VAT 200,000 øre; 70% taxable activity = 7000 bp
        long deductible = calculator.calculateInputVatDeduction(200_000L, 7_000L);
        assertEquals(140_000L, deductible);
    }

    @Test
    @DisplayName("zero deduction rate blocks all input VAT recovery")
    void calculateInputVatDeduction_zeroRate() {
        long deductible = calculator.calculateInputVatDeduction(500_000L, 0L);
        assertEquals(0L, deductible);
    }

    // --- calculateNetVat ---

    @Test
    @DisplayName("net VAT is positive when output exceeds input")
    void calculateNetVat_payable() {
        long net = calculator.calculateNetVat(500_000L, 200_000L);
        assertEquals(300_000L, net);
    }

    @Test
    @DisplayName("net VAT is negative when input exceeds output (refund scenario)")
    void calculateNetVat_claimable() {
        long net = calculator.calculateNetVat(100_000L, 400_000L);
        assertEquals(-300_000L, net);
    }

    @Test
    @DisplayName("net VAT is zero when output equals input")
    void calculateNetVat_zero() {
        long net = calculator.calculateNetVat(250_000L, 250_000L);
        assertEquals(0L, net);
    }

    // --- determineResult ---

    @Test
    @DisplayName("positive net VAT → Payable")
    void determineResult_payable() {
        VatResult result = calculator.determineResult(150_000L);
        assertInstanceOf(VatResult.Payable.class, result);
        assertEquals(150_000L, ((VatResult.Payable) result).amount());
    }

    @Test
    @DisplayName("negative net VAT → Claimable (absolute value stored)")
    void determineResult_claimable() {
        VatResult result = calculator.determineResult(-200_000L);
        assertInstanceOf(VatResult.Claimable.class, result);
        assertEquals(200_000L, ((VatResult.Claimable) result).amount()); // absolute value
    }

    @Test
    @DisplayName("zero net VAT → Zero (nulindberetning)")
    void determineResult_zero() {
        VatResult result = calculator.determineResult(0L);
        assertInstanceOf(VatResult.Zero.class, result);
    }

    @Test
    @DisplayName("round-trip: calculate net then determine result — payable scenario")
    void roundTrip_netAndResult_payable() {
        long output = calculator.calculateOutputVat(4_000_000L, 2500L); // 1,000,000 øre
        long input  = calculator.calculateInputVatDeduction(500_000L, 10_000L); // 500,000 øre
        long net    = calculator.calculateNetVat(output, input);
        VatResult result = calculator.determineResult(net);

        assertInstanceOf(VatResult.Payable.class, result);
        assertEquals(500_000L, ((VatResult.Payable) result).amount());
    }
}
