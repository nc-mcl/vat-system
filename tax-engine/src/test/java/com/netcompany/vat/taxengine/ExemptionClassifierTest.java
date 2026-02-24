package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
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
 * Unit tests for {@link ExemptionClassifier}.
 *
 * <p>Verifies that each DK tax code is correctly confirmed against the plugin,
 * with the right rate, reverse-charge flag, and effective date.
 *
 * <p>Danish categories tested:
 * <ul>
 *   <li>STANDARD — 25% MOMS (ML §4)</li>
 *   <li>ZERO_RATED — exports, intra-EU goods (ML §34)</li>
 *   <li>EXEMPT — healthcare, education, financial services (ML §13)</li>
 *   <li>REVERSE_CHARGE — cross-border B2B services, EU goods (ML §46)</li>
 *   <li>OUT_OF_SCOPE — wages, dividends, equity transfers</li>
 * </ul>
 */
@DisplayName("ExemptionClassifier")
class ExemptionClassifierTest {

    private ExemptionClassifier classifier;
    private JurisdictionPlugin dkPlugin;

    private static final LocalDate TX_DATE = LocalDate.of(2026, 2, 1);

    @BeforeEach
    void setUp() {
        classifier = new ExemptionClassifier(new RateResolver());
        dkPlugin = new DkJurisdictionPlugin();
    }

    @Test
    @DisplayName("STANDARD → rate 2500, isReverseCharge false")
    void classifyStandard() {
        Transaction tx = buildTx(TaxCode.STANDARD, 2500L, false);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        TaxClassification cls = ((Result.Ok<TaxClassification>) result).value();
        assertEquals(TaxCode.STANDARD, cls.taxCode());
        assertEquals(2500L, cls.rateInBasisPoints());
        assertFalse(cls.isReverseCharge());
        assertEquals(TX_DATE, cls.effectiveDate());
    }

    @Test
    @DisplayName("ZERO_RATED → rate 0, isReverseCharge false")
    void classifyZeroRated() {
        Transaction tx = buildTx(TaxCode.ZERO_RATED, 0L, false);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        TaxClassification cls = ((Result.Ok<TaxClassification>) result).value();
        assertEquals(TaxCode.ZERO_RATED, cls.taxCode());
        assertEquals(0L, cls.rateInBasisPoints());
        assertFalse(cls.isReverseCharge());
    }

    @Test
    @DisplayName("EXEMPT (e.g. medical services ML §13(1)(1)) → rate -1, isReverseCharge false")
    void classifyExempt_medicalServices() {
        Transaction tx = buildTx(TaxCode.EXEMPT, -1L, false);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        TaxClassification cls = ((Result.Ok<TaxClassification>) result).value();
        assertEquals(TaxCode.EXEMPT, cls.taxCode());
        assertEquals(-1L, cls.rateInBasisPoints()); // not applicable
        assertFalse(cls.isReverseCharge());
    }

    @Test
    @DisplayName("REVERSE_CHARGE (cross-border B2B service) → rate 2500, isReverseCharge true")
    void classifyReverseCharge_crossBorderService() {
        Transaction tx = buildTx(TaxCode.REVERSE_CHARGE, 2500L, true);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        TaxClassification cls = ((Result.Ok<TaxClassification>) result).value();
        assertEquals(TaxCode.REVERSE_CHARGE, cls.taxCode());
        assertEquals(2500L, cls.rateInBasisPoints());
        assertTrue(cls.isReverseCharge());
    }

    @Test
    @DisplayName("OUT_OF_SCOPE (wages, dividends) → rate -1, isReverseCharge false")
    void classifyOutOfScope() {
        Transaction tx = buildTx(TaxCode.OUT_OF_SCOPE, -1L, false);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        TaxClassification cls = ((Result.Ok<TaxClassification>) result).value();
        assertEquals(TaxCode.OUT_OF_SCOPE, cls.taxCode());
        assertEquals(-1L, cls.rateInBasisPoints());
        assertFalse(cls.isReverseCharge());
    }

    @Test
    @DisplayName("effective date is set from transaction date, not classification date")
    void classifyUsesTransactionDateAsEffectiveDate() {
        Transaction tx = buildTx(TaxCode.STANDARD, 2500L, false);

        Result<TaxClassification> result = classifier.classifyTransaction(tx, dkPlugin);

        assertTrue(result.isOk());
        // The classifier sets the effective date from the transaction date
        assertEquals(TX_DATE, ((Result.Ok<TaxClassification>) result).value().effectiveDate());
    }

    // --- helpers ---

    private Transaction buildTx(TaxCode taxCode, long rateBp, boolean isReverseCharge) {
        TaxClassification cls = new TaxClassification(taxCode, rateBp, isReverseCharge, TX_DATE);
        return new Transaction(
                UUID.randomUUID(),
                JurisdictionCode.DK,
                UUID.randomUUID(),
                UUID.randomUUID(),
                TX_DATE,
                "Test: " + taxCode.name(),
                MonetaryAmount.ofOere(500_000L),
                cls,
                Instant.now()
        );
    }
}
