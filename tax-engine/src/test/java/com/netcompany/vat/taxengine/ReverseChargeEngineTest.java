package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatRuleError;
import com.netcompany.vat.domain.dk.DkJurisdictionPlugin;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReverseChargeEngine}.
 *
 * <p>Covers: applicability check, VAT amount calculation, VAT number validation.
 * Test data reflects Danish reverse-charge scenarios (cross-border B2B services).
 */
@DisplayName("ReverseChargeEngine")
class ReverseChargeEngineTest {

    private ReverseChargeEngine engine;
    private JurisdictionPlugin dkPlugin;

    @BeforeEach
    void setUp() {
        engine = new ReverseChargeEngine(new VatCalculator());
        dkPlugin = new DkJurisdictionPlugin();
    }

    // --- isReverseChargeApplicable ---

    @Test
    @DisplayName("returns true for REVERSE_CHARGE classified transaction")
    void isApplicable_reverseChargeTx() {
        Transaction tx = buildTx(TaxCode.REVERSE_CHARGE, 1_000_000L);

        Result<Boolean> result = engine.isReverseChargeApplicable(tx, dkPlugin);

        assertTrue(result.isOk());
        assertTrue(((Result.Ok<Boolean>) result).value());
    }

    @Test
    @DisplayName("returns false for STANDARD classified transaction")
    void isApplicable_standardTx() {
        Transaction tx = buildTx(TaxCode.STANDARD, 500_000L);

        Result<Boolean> result = engine.isReverseChargeApplicable(tx, dkPlugin);

        assertTrue(result.isOk());
        assertFalse(((Result.Ok<Boolean>) result).value());
    }

    @Test
    @DisplayName("returns false for EXEMPT classified transaction")
    void isApplicable_exemptTx() {
        Transaction tx = buildTx(TaxCode.EXEMPT, 200_000L);

        Result<Boolean> result = engine.isReverseChargeApplicable(tx, dkPlugin);

        assertTrue(result.isOk());
        assertFalse(((Result.Ok<Boolean>) result).value());
    }

    // --- applyReverseCharge ---

    @Test
    @DisplayName("applies 25% self-assessment to 10,000 DKK cross-border service")
    void applyReverseCharge_crossBorderService() {
        // Danish business buys 10,000 DKK IT consulting from German supplier
        // Base: 1,000,000 øre; rate: 2500 bp (25%)
        Transaction tx = buildTx(TaxCode.REVERSE_CHARGE, 1_000_000L);

        Result<ReverseChargeResult> result = engine.applyReverseCharge(tx, 2500L);

        assertTrue(result.isOk());
        ReverseChargeResult rc = ((Result.Ok<ReverseChargeResult>) result).value();
        assertEquals(250_000L, rc.outputVatOere());   // 2,500 DKK self-assessed output VAT
        assertEquals(250_000L, rc.inputVatOere());    // 2,500 DKK input VAT deduction (fully deductible)
        assertEquals(2500L,    rc.rateInBasisPoints());
        assertEquals(1_000_000L, rc.baseAmountOere());
    }

    @Test
    @DisplayName("net VAT impact is zero for fully deductible reverse charge")
    void applyReverseCharge_netImpactIsZero() {
        Transaction tx = buildTx(TaxCode.REVERSE_CHARGE, 5_000_000L);

        Result<ReverseChargeResult> result = engine.applyReverseCharge(tx, 2500L);

        assertTrue(result.isOk());
        assertEquals(0L, ((Result.Ok<ReverseChargeResult>) result).value().netVatImpactOere());
    }

    @Test
    @DisplayName("returns InvalidTransaction error for non-REVERSE_CHARGE transaction")
    void applyReverseCharge_nonRcTx_returnsError() {
        Transaction tx = buildTx(TaxCode.STANDARD, 1_000_000L);

        Result<ReverseChargeResult> result = engine.applyReverseCharge(tx, 2500L);

        assertTrue(result.isErr());
        assertInstanceOf(VatRuleError.InvalidTransaction.class,
                ((Result.Err<ReverseChargeResult>) result).error());
    }

    @Test
    @DisplayName("truncates toward zero on fractional reverse charge amounts")
    void applyReverseCharge_truncatesOnFractionalAmount() {
        // 1 øre at 25% → 0.25 øre → truncated to 0
        Transaction tx = buildTx(TaxCode.REVERSE_CHARGE, 1L);

        Result<ReverseChargeResult> result = engine.applyReverseCharge(tx, 2500L);

        assertTrue(result.isOk());
        assertEquals(0L, ((Result.Ok<ReverseChargeResult>) result).value().outputVatOere());
    }

    // --- validateCounterpartyVatNumber ---

    @Test
    @DisplayName("returns Ok when counterparty has a valid VAT number")
    void validateVatNumber_valid() {
        Counterparty counterparty = new Counterparty(
                UUID.randomUUID(), JurisdictionCode.DK, "DK12345678", "Acme A/S");

        Result<Counterparty> result = engine.validateCounterpartyVatNumber(counterparty);

        assertTrue(result.isOk());
        assertEquals(counterparty, ((Result.Ok<Counterparty>) result).value());
    }

    @Test
    @DisplayName("returns MissingVatNumber when VAT number is null")
    void validateVatNumber_null() {
        Counterparty counterparty = new Counterparty(
                UUID.randomUUID(), JurisdictionCode.DK, null, "Acme A/S");

        Result<Counterparty> result = engine.validateCounterpartyVatNumber(counterparty);

        assertTrue(result.isErr());
        assertInstanceOf(VatRuleError.MissingVatNumber.class,
                ((Result.Err<Counterparty>) result).error());
    }

    @Test
    @DisplayName("returns MissingVatNumber when VAT number is blank")
    void validateVatNumber_blank() {
        Counterparty counterparty = new Counterparty(
                UUID.randomUUID(), JurisdictionCode.DK, "   ", "Acme A/S");

        Result<Counterparty> result = engine.validateCounterpartyVatNumber(counterparty);

        assertTrue(result.isErr());
        assertInstanceOf(VatRuleError.MissingVatNumber.class,
                ((Result.Err<Counterparty>) result).error());
    }

    @Test
    @DisplayName("MissingVatNumber error contains the counterparty ID")
    void validateVatNumber_errorContainsCounterpartyId() {
        UUID cpId = UUID.randomUUID();
        Counterparty counterparty = new Counterparty(cpId, JurisdictionCode.DK, null, "Anon");

        Result<Counterparty> result = engine.validateCounterpartyVatNumber(counterparty);

        VatRuleError.MissingVatNumber err =
                (VatRuleError.MissingVatNumber) ((Result.Err<Counterparty>) result).error();
        assertEquals(cpId.toString(), err.counterpartyId());
    }

    // --- helpers ---

    private Transaction buildTx(TaxCode taxCode, long amountOere) {
        long rate = switch (taxCode) {
            case STANDARD, REVERSE_CHARGE -> 2500L;
            case ZERO_RATED -> 0L;
            case EXEMPT, OUT_OF_SCOPE -> -1L;
        };
        TaxClassification classification = new TaxClassification(
                taxCode, rate, taxCode == TaxCode.REVERSE_CHARGE, LocalDate.of(2026, 1, 15));
        return new Transaction(
                UUID.randomUUID(),
                JurisdictionCode.DK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 15),
                "Test transaction",
                MonetaryAmount.ofOere(amountOere),
                classification,
                Instant.now()
        );
    }
}
