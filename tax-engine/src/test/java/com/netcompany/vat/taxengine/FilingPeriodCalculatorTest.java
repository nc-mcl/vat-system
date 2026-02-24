package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
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
 * Unit tests for {@link FilingPeriodCalculator}.
 *
 * <p>Cadence thresholds and deadline rules are verified against SKAT-validated BA documents:
 * <ul>
 *   <li>&lt; 5,000,000 DKK annual turnover → SEMI_ANNUAL</li>
 *   <li>5,000,000–50,000,000 DKK → QUARTERLY</li>
 *   <li>&gt; 50,000,000 DKK → MONTHLY</li>
 *   <li>MONTHLY deadline: 25th of the following month</li>
 *   <li>QUARTERLY / SEMI_ANNUAL deadline: 1st of the 3rd month after period end</li>
 * </ul>
 */
@DisplayName("FilingPeriodCalculator")
class FilingPeriodCalculatorTest {

    private FilingPeriodCalculator calculator;
    private JurisdictionPlugin dkPlugin;

    @BeforeEach
    void setUp() {
        calculator = new FilingPeriodCalculator();
        dkPlugin = new DkJurisdictionPlugin();
    }

    // --- determineFilingCadence ---

    @Test
    @DisplayName("turnover below 5M DKK → SEMI_ANNUAL (halvårlig)")
    void cadence_semiAnnual_belowFiveMillion() {
        // 3,000,000 DKK = 300,000,000 øre
        FilingCadence cadence = calculator.determineFilingCadence(300_000_000L, dkPlugin);
        assertEquals(FilingCadence.SEMI_ANNUAL, cadence);
    }

    @Test
    @DisplayName("turnover exactly at 5M DKK boundary → QUARTERLY (5M is quarterly threshold)")
    void cadence_quarterly_atFiveMillionBoundary() {
        // Exactly 5,000,000 DKK = 500,000,000 øre
        // DK plugin: > 500M øre → quarterly; ≤ 500M øre → semi-annual
        // 500,000,001 øre is above the semi-annual threshold
        FilingCadence cadence = calculator.determineFilingCadence(500_000_001L, dkPlugin);
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    @Test
    @DisplayName("turnover 25M DKK → QUARTERLY")
    void cadence_quarterly_midRange() {
        // 25,000,000 DKK = 2,500,000,000 øre
        FilingCadence cadence = calculator.determineFilingCadence(2_500_000_000L, dkPlugin);
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    @Test
    @DisplayName("turnover above 50M DKK → MONTHLY")
    void cadence_monthly_aboveFiftyMillion() {
        // 75,000,000 DKK = 7,500,000,000 øre
        FilingCadence cadence = calculator.determineFilingCadence(7_500_000_000L, dkPlugin);
        assertEquals(FilingCadence.MONTHLY, cadence);
    }

    @Test
    @DisplayName("turnover exactly at 50M DKK boundary → QUARTERLY (50M is monthly threshold)")
    void cadence_quarterly_atFiftyMillionBoundary() {
        // Exactly 50,000,000 DKK = 5,000,000,000 øre
        // DK plugin: > 5,000,000,000 → monthly; ≤ 5,000,000,000 → quarterly
        FilingCadence cadence = calculator.determineFilingCadence(5_000_000_000L, dkPlugin);
        assertEquals(FilingCadence.QUARTERLY, cadence);
    }

    // --- calculateFilingDeadline ---

    @Test
    @DisplayName("Q1 (Jan–Mar) quarterly deadline → June 1 (1st of 3rd month after period end)")
    void deadline_q1_quarterly_isJune1st() {
        TaxPeriod q1 = buildPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        LocalDate deadline = calculator.calculateFilingDeadline(q1, dkPlugin);

        assertEquals(LocalDate.of(2026, 6, 1), deadline);
    }

    @Test
    @DisplayName("Q2 (Apr–Jun) quarterly deadline → September 1 (1st of 3rd month after period end)")
    void deadline_q2_quarterly_isSept1st() {
        TaxPeriod q2 = buildPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        LocalDate deadline = calculator.calculateFilingDeadline(q2, dkPlugin);

        assertEquals(LocalDate.of(2026, 9, 1), deadline);
    }

    @Test
    @DisplayName("H1 semi-annual (Jan–Jun) deadline → September 1 (1st of 3rd month after period end)")
    void deadline_h1_semiAnnual_isSept1st() {
        TaxPeriod h1 = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                FilingCadence.SEMI_ANNUAL, TaxPeriodStatus.OPEN);

        LocalDate deadline = calculator.calculateFilingDeadline(h1, dkPlugin);

        assertEquals(LocalDate.of(2026, 9, 1), deadline);
    }

    @Test
    @DisplayName("January monthly period deadline → February 25 (25th of following month)")
    void deadline_january_monthly_isFeb25th() {
        TaxPeriod jan = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                FilingCadence.MONTHLY, TaxPeriodStatus.OPEN);

        LocalDate deadline = calculator.calculateFilingDeadline(jan, dkPlugin);

        assertEquals(LocalDate.of(2026, 2, 25), deadline);
    }

    // --- isFilingOverdue ---

    @Test
    @DisplayName("before deadline and OPEN → not overdue")
    void notOverdue_beforeDeadline() {
        TaxPeriod q1 = buildPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        // Q1 QUARTERLY deadline is June 1; check on May 31 (one day before)
        Instant beforeDeadline = Instant.parse("2026-05-31T12:00:00Z");

        assertFalse(calculator.isFilingOverdue(q1, dkPlugin, beforeDeadline));
    }

    @Test
    @DisplayName("on the deadline date → not overdue (deadline is inclusive)")
    void notOverdue_onDeadlineDate() {
        TaxPeriod q1 = buildPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        // Q1 QUARTERLY deadline is June 1 — filing on deadline date is still on time
        Instant onDeadline = Instant.parse("2026-06-01T23:59:59Z");

        assertFalse(calculator.isFilingOverdue(q1, dkPlugin, onDeadline));
    }

    @Test
    @DisplayName("after deadline and OPEN → overdue")
    void overdue_afterDeadline() {
        TaxPeriod q1 = buildPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        // Q1 QUARTERLY deadline is June 1; check on June 2
        Instant afterDeadline = Instant.parse("2026-06-02T00:00:00Z");

        assertTrue(calculator.isFilingOverdue(q1, dkPlugin, afterDeadline));
    }

    @Test
    @DisplayName("after deadline but already FILED → not overdue")
    void notOverdue_alreadyFiled() {
        TaxPeriod filedPeriod = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.FILED);
        // After the Q1 QUARTERLY deadline (June 1)
        Instant afterDeadline = Instant.parse("2026-06-15T00:00:00Z");

        assertFalse(calculator.isFilingOverdue(filedPeriod, dkPlugin, afterDeadline));
    }

    @Test
    @DisplayName("after deadline but ASSESSED → not overdue (already processed)")
    void notOverdue_alreadyAssessed() {
        TaxPeriod assessedPeriod = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.ASSESSED);
        // After the Q1 QUARTERLY deadline (June 1)
        Instant afterDeadline = Instant.parse("2026-06-15T00:00:00Z");

        assertFalse(calculator.isFilingOverdue(assessedPeriod, dkPlugin, afterDeadline));
    }

    // --- helpers ---

    private TaxPeriod buildPeriod(LocalDate start, LocalDate end) {
        return new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                start, end,
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
    }
}
