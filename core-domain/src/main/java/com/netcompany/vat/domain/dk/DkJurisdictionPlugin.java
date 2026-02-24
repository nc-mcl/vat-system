package com.netcompany.vat.domain.dk;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;

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
 * <h2>Filing Deadlines (per SKAT cadence rules)</h2>
 * <ul>
 *   <li>MONTHLY: 25th of the month following the period end (e.g. Jan → Feb 25)</li>
 *   <li>QUARTERLY: 1st of the 3rd month after the period ends (e.g. Q1 Mar 31 → Jun 1)</li>
 *   <li>SEMI_ANNUAL: 1st of the 3rd month after the period ends (e.g. H1 Jun 30 → Sep 1)</li>
 * </ul>
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
     * Calculates the SKAT filing deadline based on the period's cadence.
     *
     * <ul>
     *   <li>MONTHLY    — 25th of the month following the period end (e.g. Jan → Feb 25)</li>
     *   <li>QUARTERLY  — 1st of the 3rd month after the period ends (e.g. Q1 Mar 31 → Jun 1)</li>
     *   <li>SEMI_ANNUAL — 1st of the 3rd month after the period ends (e.g. H1 Jun 30 → Sep 1)</li>
     *   <li>ANNUAL     — 1st of the 3rd month after the period ends (same formula as quarterly)</li>
     * </ul>
     */
    @Override
    public LocalDate calculateFilingDeadline(TaxPeriod period) {
        return switch (period.cadence()) {
            case MONTHLY -> period.endDate().plusMonths(1).withDayOfMonth(25);
            case QUARTERLY, SEMI_ANNUAL, ANNUAL -> period.endDate().plusMonths(3).withDayOfMonth(1);
        };
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
