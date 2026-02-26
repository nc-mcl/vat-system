package com.netcompany.vat.domain.dk;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DkJurisdictionPlugin}.
 *
 * <p>Verifies Danish MOMS rules against SKAT-validated domain knowledge
 * (dk-vat-rules-validated.md):
 * <ul>
 *   <li>Rule 2.1 — Standard rate is 25% (2500 basis points)</li>
 *   <li>Rule 3.1/3.2/3.3 — Filing cadence thresholds</li>
 *   <li>Rule 3.4 — Filing deadlines per cadence</li>
 * </ul>
 */
@DisplayName("DkJurisdictionPlugin")
class DkJurisdictionPluginTest {

    private DkJurisdictionPlugin plugin;
    private static final LocalDate RATE_DATE = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        plugin = new DkJurisdictionPlugin();
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCode() returns DK")
    void getCode_returnsDK() {
        assertEquals(JurisdictionCode.DK, plugin.getCode());
    }

    @Test
    @DisplayName("getAuthorityName() returns SKAT")
    void getAuthorityName_returnsSkat() {
        assertEquals("SKAT", plugin.getAuthorityName());
    }

    // ── Tax Rates (Rule 2.1) ──────────────────────────────────────────────────

    @Test
    @DisplayName("Rule 2.1: STANDARD rate is 2500 basis points (25%)")
    void rate_standard_is2500BasisPoints() {
        assertEquals(2500L, plugin.getVatRateInBasisPoints(TaxCode.STANDARD, RATE_DATE));
    }

    @Test
    @DisplayName("ZERO_RATED rate is 0 basis points")
    void rate_zeroRated_is0BasisPoints() {
        assertEquals(0L, plugin.getVatRateInBasisPoints(TaxCode.ZERO_RATED, RATE_DATE));
    }

    @Test
    @DisplayName("EXEMPT rate is -1 (not applicable — no VAT recovery)")
    void rate_exempt_isMinusOne() {
        assertEquals(-1L, plugin.getVatRateInBasisPoints(TaxCode.EXEMPT, RATE_DATE));
    }

    @Test
    @DisplayName("REVERSE_CHARGE rate is 2500 basis points (buyer self-assesses at standard rate)")
    void rate_reverseCharge_is2500BasisPoints() {
        assertEquals(2500L, plugin.getVatRateInBasisPoints(TaxCode.REVERSE_CHARGE, RATE_DATE));
    }

    @Test
    @DisplayName("OUT_OF_SCOPE rate is -1 (not subject to VAT)")
    void rate_outOfScope_isMinusOne() {
        assertEquals(-1L, plugin.getVatRateInBasisPoints(TaxCode.OUT_OF_SCOPE, RATE_DATE));
    }

    @Test
    @DisplayName("Denmark has NO reduced VAT rate — only 25% standard or 0%/exempt")
    void noReducedRate_onlyStandardOrZeroOrExempt() {
        // Verifies Rule 2.1: single standard rate; there are no reduced rates in DK
        long standardRate = plugin.getVatRateInBasisPoints(TaxCode.STANDARD, RATE_DATE);
        long zeroRate     = plugin.getVatRateInBasisPoints(TaxCode.ZERO_RATED, RATE_DATE);

        // Only 0%, 25%, or N/A (-1) are valid rates for DK
        assertEquals(2500L, standardRate, "DK standard rate must be exactly 25%");
        assertEquals(0L, zeroRate, "DK zero rate must be exactly 0%");
        // No assertion for a 'reduced' rate because it doesn't exist in DK
    }

    // ── Filing Cadence (Rules 3.1–3.3) ────────────────────────────────────────

    @Test
    @DisplayName("Rule 3.1: turnover < 5M DKK → SEMI_ANNUAL (halvårlig)")
    void cadence_belowFiveMillionDkk_isSemiAnnual() {
        // 4,000,000 DKK = 400,000,000 øre
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(400_000_000L));
        assertEquals(FilingCadence.SEMI_ANNUAL, cadence);
    }

    @Test
    @DisplayName("Rule 3.1: turnover just below 5M DKK threshold → SEMI_ANNUAL")
    void cadence_justBelowSemiAnnualThreshold_isSemiAnnual() {
        // 500,000,000 øre = exactly 5M DKK; threshold is ABOVE this for quarterly
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(500_000_000L));
        assertEquals(FilingCadence.SEMI_ANNUAL, cadence);
    }

    @Test
    @DisplayName("Rule 3.2: turnover above 5M DKK → QUARTERLY (starts at 500,000,001 øre)")
    void cadence_justAboveFiveMillionDkk_isQuarterly() {
        // One øre above the semi-annual threshold
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(500_000_001L));
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    @Test
    @DisplayName("Rule 3.2: turnover 25M DKK (mid-range) → QUARTERLY")
    void cadence_midRangeQuarterly() {
        // 25,000,000 DKK = 2,500,000,000 øre
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(2_500_000_000L));
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    @Test
    @DisplayName("Rule 3.2: turnover exactly at 50M DKK boundary → QUARTERLY (not yet monthly)")
    void cadence_exactlyAtMonthlyThreshold_isQuarterly() {
        // 50,000,000 DKK = 5,000,000,000 øre — threshold is strictly ABOVE this for monthly
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(5_000_000_000L));
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    @Test
    @DisplayName("Rule 3.3: turnover above 50M DKK → MONTHLY")
    void cadence_aboveFiftyMillionDkk_isMonthly() {
        // 75,000,000 DKK = 7,500,000,000 øre
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(7_500_000_000L));
        assertEquals(FilingCadence.MONTHLY, cadence);
    }

    @Test
    @DisplayName("Rule 3.3: turnover just above 50M DKK → MONTHLY (threshold crossing)")
    void cadence_justAboveMonthlyThreshold_isMonthly() {
        FilingCadence cadence = plugin.determineFilingCadence(MonetaryAmount.ofOere(5_000_000_001L));
        assertEquals(FilingCadence.MONTHLY, cadence);
    }

    // ── Filing Deadlines (Rule 3.4) ────────────────────────────────────────────

    @Test
    @DisplayName("Rule 3.4: MONTHLY January period → deadline February 25")
    void deadline_monthly_january_isFebruary25() {
        TaxPeriod jan = buildPeriod(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), FilingCadence.MONTHLY);
        assertEquals(LocalDate.of(2026, 2, 25), plugin.calculateFilingDeadline(jan));
    }

    @Test
    @DisplayName("Rule 3.4: MONTHLY December period → deadline January 25 of following year")
    void deadline_monthly_december_isJanuary25NextYear() {
        TaxPeriod dec = buildPeriod(
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31), FilingCadence.MONTHLY);
        assertEquals(LocalDate.of(2026, 1, 25), plugin.calculateFilingDeadline(dec));
    }

    @Test
    @DisplayName("Rule 3.4: MONTHLY November period → deadline December 25")
    void deadline_monthly_november_isDecember25() {
        TaxPeriod nov = buildPeriod(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), FilingCadence.MONTHLY);
        assertEquals(LocalDate.of(2026, 12, 25), plugin.calculateFilingDeadline(nov));
    }

    @Test
    @DisplayName("Rule 3.4: QUARTERLY Q1 (Jan-Mar) → deadline June 1")
    void deadline_quarterly_q1_isJune1() {
        TaxPeriod q1 = buildPeriod(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), FilingCadence.QUARTERLY);
        assertEquals(LocalDate.of(2026, 6, 1), plugin.calculateFilingDeadline(q1));
    }

    @Test
    @DisplayName("Rule 3.4: QUARTERLY Q2 (Apr-Jun) → deadline September 1")
    void deadline_quarterly_q2_isSeptember1() {
        TaxPeriod q2 = buildPeriod(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), FilingCadence.QUARTERLY);
        assertEquals(LocalDate.of(2026, 9, 1), plugin.calculateFilingDeadline(q2));
    }

    @Test
    @DisplayName("Rule 3.4: QUARTERLY Q3 (Jul-Sep) → deadline December 1")
    void deadline_quarterly_q3_isDecember1() {
        TaxPeriod q3 = buildPeriod(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), FilingCadence.QUARTERLY);
        assertEquals(LocalDate.of(2026, 12, 1), plugin.calculateFilingDeadline(q3));
    }

    @Test
    @DisplayName("Rule 3.4: QUARTERLY Q4 (Oct-Dec) → deadline March 1 of following year")
    void deadline_quarterly_q4_isMarch1NextYear() {
        TaxPeriod q4 = buildPeriod(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), FilingCadence.QUARTERLY);
        assertEquals(LocalDate.of(2027, 3, 1), plugin.calculateFilingDeadline(q4));
    }

    @Test
    @DisplayName("Rule 3.4: SEMI_ANNUAL H1 (Jan-Jun) → deadline September 1")
    void deadline_semiAnnual_h1_isSeptember1() {
        TaxPeriod h1 = buildPeriod(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), FilingCadence.SEMI_ANNUAL);
        assertEquals(LocalDate.of(2026, 9, 1), plugin.calculateFilingDeadline(h1));
    }

    @Test
    @DisplayName("Rule 3.4: SEMI_ANNUAL H2 (Jul-Dec) → deadline March 1 of following year")
    void deadline_semiAnnual_h2_isMarch1NextYear() {
        TaxPeriod h2 = buildPeriod(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), FilingCadence.SEMI_ANNUAL);
        assertEquals(LocalDate.of(2027, 3, 1), plugin.calculateFilingDeadline(h2));
    }

    @Test
    @DisplayName("Risk R2: leap year Feb deadline — MONTHLY Jan 2028 → Feb 25 2028 (leap year)")
    void deadline_monthly_leapYear_february() {
        // 2028 is a leap year (divisible by 4). Feb 25 is valid in any year.
        TaxPeriod jan2028 = buildPeriod(
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 1, 31), FilingCadence.MONTHLY);
        LocalDate deadline = plugin.calculateFilingDeadline(jan2028);
        // Deadline is Feb 25 regardless of leap year — still a valid date
        assertEquals(LocalDate.of(2028, 2, 25), deadline);
        // Verify Feb 25 exists in 2028 (leap year has 29 days in Feb)
        assertTrue(deadline.getDayOfMonth() <= java.time.Month.FEBRUARY.length(true));
    }

    @Test
    @DisplayName("Risk R2: month-end boundary — QUARTERLY period ending Oct 31 → deadline Jan 1 next year")
    void deadline_quarterly_october_endBoundary() {
        // Oct 31 + 3 months = Jan 31 (plusMonths is calendar-month aware)
        // withDayOfMonth(1) = Jan 1, 2027
        TaxPeriod q = buildPeriod(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31), FilingCadence.QUARTERLY);
        assertEquals(LocalDate.of(2027, 1, 1), plugin.calculateFilingDeadline(q));
    }

    // ── Reverse Charge ────────────────────────────────────────────────────────

    @Test
    @DisplayName("isReverseChargeApplicable returns true for REVERSE_CHARGE transaction")
    void reverseCharge_applicable_forReverseChargeTransaction() {
        Transaction tx = buildTransaction(TaxCode.REVERSE_CHARGE, 2500L, true);
        assertTrue(plugin.isReverseChargeApplicable(tx));
    }

    @Test
    @DisplayName("isReverseChargeApplicable returns false for STANDARD transaction")
    void reverseCharge_notApplicable_forStandardTransaction() {
        Transaction tx = buildTransaction(TaxCode.STANDARD, 2500L, false);
        assertFalse(plugin.isReverseChargeApplicable(tx));
    }

    @Test
    @DisplayName("isReverseChargeApplicable returns false for EXEMPT transaction")
    void reverseCharge_notApplicable_forExemptTransaction() {
        Transaction tx = buildTransaction(TaxCode.EXEMPT, -1L, false);
        assertFalse(plugin.isReverseChargeApplicable(tx));
    }

    // ── ViDA ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isVidaEnabled returns false in Phase 1 (ViDA is Phase 2 / 2028)")
    void isVidaEnabled_returnsFalse() {
        assertFalse(plugin.isVidaEnabled(),
                "ViDA DRR is not active for DK in Phase 1 — expected enabled by 2028");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TaxPeriod buildPeriod(LocalDate start, LocalDate end, FilingCadence cadence) {
        return new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK, start, end,
                cadence, TaxPeriodStatus.OPEN);
    }

    private Transaction buildTransaction(TaxCode taxCode, long rateBp, boolean isReverseCharge) {
        TaxClassification cls = new TaxClassification(taxCode, rateBp, isReverseCharge, RATE_DATE);
        return new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK,
                UUID.randomUUID(), null,
                RATE_DATE, "Test: " + taxCode.name(),
                MonetaryAmount.ofOere(100_000L), cls, Instant.now());
    }
}
