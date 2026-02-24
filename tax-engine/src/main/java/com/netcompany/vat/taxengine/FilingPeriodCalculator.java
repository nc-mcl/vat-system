package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Computes filing cadence, filing deadlines, and overdue status for a tax period.
 *
 * <p>All jurisdiction-specific logic (thresholds, deadline rules) is fully delegated to the
 * {@link JurisdictionPlugin}. This class contains no hardcoded country values.
 *
 * <h2>Danish cadence thresholds (SKAT, verified from BA analysis)</h2>
 * <ul>
 *   <li>Annual turnover &gt; 50,000,000 DKK → {@code MONTHLY}</li>
 *   <li>Annual turnover 5,000,000–50,000,000 DKK → {@code QUARTERLY} (also default for
 *       new businesses in their first 18 months)</li>
 *   <li>Annual turnover &lt; 5,000,000 DKK → {@code SEMI_ANNUAL}</li>
 * </ul>
 *
 * <h2>Danish filing deadlines (SKAT, verified)</h2>
 * The DK plugin applies cadence-specific deadlines:
 * <ul>
 *   <li>MONTHLY: 25th of the following month (e.g. Jan → Feb 25)</li>
 *   <li>QUARTERLY: 1st of the 3rd month after period end (e.g. Q1 Mar 31 → Jun 1)</li>
 *   <li>SEMI_ANNUAL: 1st of the 3rd month after period end (e.g. H1 Jun 30 → Sep 1)</li>
 * </ul>
 *
 * @implNote Danish example — a business with 30M DKK annual turnover:
 * {@code determineFilingCadence(30_000_000_00L, plugin)} returns {@code QUARTERLY}.
 * For Q1 2026 (Jan 1 – Mar 31), {@code calculateFilingDeadline(period, plugin)} returns
 * {@code 2026-06-01}.
 */
public final class FilingPeriodCalculator {

    /**
     * Determines the filing cadence for a taxpayer based on annual turnover.
     *
     * <p>Delegates entirely to the plugin's threshold logic. No DK-specific values here.
     *
     * @param annualTurnoverOere annual taxable turnover in the smallest currency unit (øre for DKK)
     * @param plugin             the active jurisdiction plugin
     * @return the filing frequency the taxpayer must use
     */
    public FilingCadence determineFilingCadence(long annualTurnoverOere, JurisdictionPlugin plugin) {
        return plugin.determineFilingCadence(MonetaryAmount.ofOere(annualTurnoverOere));
    }

    /**
     * Calculates the statutory filing deadline for the given period.
     *
     * <p>Once the deadline passes without a filing, the taxpayer risks a provisional
     * assessment (foreløbig fastsættelse) and a DKK 1,400 late-filing fee per period.
     *
     * @param period the tax period to calculate the deadline for
     * @param plugin the active jurisdiction plugin
     * @return the last date on which a filing is accepted without penalty
     */
    public LocalDate calculateFilingDeadline(TaxPeriod period, JurisdictionPlugin plugin) {
        return plugin.calculateFilingDeadline(period);
    }

    /**
     * Determines whether the filing deadline for the given period has passed.
     *
     * <p>The period is considered overdue if the deadline has passed AND the period
     * has not yet been filed (i.e. still in OPEN status). The {@code now} parameter
     * enables deterministic testing without relying on the system clock.
     *
     * @param period the tax period to check
     * @param plugin the active jurisdiction plugin
     * @param now    the current instant (UTC); typically {@code Instant.now()} in production
     * @return {@code true} if the deadline has passed as of {@code now} and
     *         the period is still open, {@code false} otherwise
     */
    public boolean isFilingOverdue(TaxPeriod period, JurisdictionPlugin plugin, Instant now) {
        if (!period.isOpen()) {
            // Already filed, assessed, or corrected — not overdue
            return false;
        }
        LocalDate deadline = calculateFilingDeadline(period, plugin);
        // Convert now to a LocalDate in UTC for comparison
        LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return today.isAfter(deadline);
    }
}
