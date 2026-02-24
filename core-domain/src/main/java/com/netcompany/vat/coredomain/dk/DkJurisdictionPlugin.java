package com.netcompany.vat.coredomain.dk;

import com.netcompany.vat.coredomain.FilingCadence;
import com.netcompany.vat.coredomain.JurisdictionCode;
import com.netcompany.vat.coredomain.MonetaryAmount;
import com.netcompany.vat.coredomain.TaxCode;
import com.netcompany.vat.coredomain.TaxPeriod;
import com.netcompany.vat.coredomain.Transaction;
import com.netcompany.vat.coredomain.jurisdiction.JurisdictionPlugin;

import java.time.LocalDate;
import java.util.Map;

/**
 * Danish VAT jurisdiction plugin — implements MOMS rules per SKAT requirements.
 *
 * <h2>Tax Rates</h2>
 * <ul>
 *   <li>STANDARD: 2500 basis points = 25.00%</li>
 *   <li>ZERO_RATED: 0 basis points = 0.00%</li>
 *   <li>EXEMPT: -1 (not applicable — no VAT charged, no recovery)</li>
 *   <li>REVERSE_CHARGE: 2500 basis points (buyer self-assesses at standard rate)</li>
 *   <li>OUT_OF_SCOPE: -1 (not subject to VAT)</li>
 * </ul>
 *
 * <h2>Filing Cadence Thresholds (annual turnover in DKK)</h2>
 * <ul>
 *   <li>&gt; 50,000,000 DKK (5,000,000,000 øre): MONTHLY</li>
 *   <li>5,000,000–50,000,000 DKK: QUARTERLY (default)</li>
 *   <li>&lt; 5,000,000 DKK (500,000,000 øre): SEMI_ANNUAL</li>
 * </ul>
 *
 * <h2>Filing Deadline</h2>
 * The 10th of the month following the period end.
 * Example: Q1 (Jan 1 – Mar 31) → deadline April 10.
 *
 * <h2>ViDA</h2>
 * Not yet active. Returns {@code false} from {@link #isVidaEnabled()} until Phase 2.
 */
public final class DkJurisdictionPlugin implements JurisdictionPlugin {

    /** 50,000,000 DKK in øre — monthly filing threshold. */
    private static final long MONTHLY_THRESHOLD_OERE = 5_000_000_000L;

    /** 5,000,000 DKK in øre — quarterly/semi-annual boundary. */
    private static final long SEMI_ANNUAL_THRESHOLD_OERE = 500_000_000L;

    /**
     * Basis-point rates by tax code. -1 signals "not applicable"
     * (rate computation should not be performed for these codes).
     */
    private static final Map<TaxCode, Long> RATES = Map.of(
            TaxCode.STANDARD,       2500L,
            TaxCode.ZERO_RATED,        0L,
            TaxCode.EXEMPT,           -1L,
            TaxCode.REVERSE_CHARGE, 2500L,  // buyer self-assesses at standard rate
            TaxCode.OUT_OF_SCOPE,     -1L
    );

    @Override
    public JurisdictionCode getCode() {
        return JurisdictionCode.DK;
    }

    /**
     * Returns the DK VAT rate for the given tax code as of the given date.
     *
     * <p>Rate history is not yet modelled — the rate is effective from system inception.
     * A future version may consult a rate schedule keyed by {@code effectiveDate}.
     *
     * @return rate in basis points, or -1 if the code is not applicable
     * @throws IllegalArgumentException if the tax code is not supported in DK
     */
    @Override
    public long getVatRateInBasisPoints(TaxCode taxCode, LocalDate effectiveDate) {
        Long rate = RATES.get(taxCode);
        if (rate == null) {
            throw new IllegalArgumentException(
                    "Tax code not supported in DK jurisdiction: " + taxCode);
        }
        return rate;
    }

    /**
     * Determines filing cadence by annual turnover thresholds per SKAT rules.
     *
     * <p>Thresholds:
     * <ul>
     *   <li>&gt; 50M DKK → MONTHLY</li>
     *   <li>5M–50M DKK → QUARTERLY</li>
     *   <li>&lt; 5M DKK → SEMI_ANNUAL</li>
     * </ul>
     */
    @Override
    public FilingCadence determineFilingCadence(MonetaryAmount annualTurnover) {
        long oere = annualTurnover.oere();
        if (oere > MONTHLY_THRESHOLD_OERE) {
            return FilingCadence.MONTHLY;
        } else if (oere > SEMI_ANNUAL_THRESHOLD_OERE) {
            return FilingCadence.QUARTERLY;
        } else {
            return FilingCadence.SEMI_ANNUAL;
        }
    }

    /**
     * Calculates the SKAT filing deadline: the 10th of the month following the period end.
     *
     * <p>Examples:
     * <ul>
     *   <li>Jan 31 (monthly) → Feb 10</li>
     *   <li>Mar 31 (Q1)      → Apr 10</li>
     *   <li>Jun 30 (Q2)      → Jul 10</li>
     * </ul>
     */
    @Override
    public LocalDate calculateFilingDeadline(TaxPeriod period) {
        // First day of the month after period end, plus 9 days = 10th of that month
        return period.endDate()
                .withDayOfMonth(1)
                .plusMonths(1)
                .plusDays(9);
    }

    /**
     * Reverse charge applies in DK when the transaction's classification is REVERSE_CHARGE.
     *
     * <p>In practice this covers inbound cross-border B2B services where the Danish business
     * is the buyer. The full evaluation (checking whether the supplier is EU-registered, etc.)
     * is performed during transaction classification in the tax-engine module.
     */
    @Override
    public boolean isReverseChargeApplicable(Transaction transaction) {
        return transaction.classification().taxCode() == TaxCode.REVERSE_CHARGE;
    }

    /**
     * ViDA Digital Reporting Requirements are not yet active for Denmark.
     * Returns {@code false} until Phase 2 activation (planned 2028).
     */
    @Override
    public boolean isVidaEnabled() {
        return false;
    }

    @Override
    public String getAuthorityName() {
        return "SKAT";
    }
}
