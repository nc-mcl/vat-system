package com.netcompany.vat.domain.jurisdiction;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.Transaction;

import java.time.LocalDate;

/**
 * Service Provider Interface that every jurisdiction must implement.
 *
 * <p>A jurisdiction plugin is the complete, self-contained description of one country's
 * VAT rules from the system's perspective. It covers:
 * <ul>
 *   <li>Tax rates (standard, reduced, zero, exempt)</li>
 *   <li>Filing cadence determination (based on annual turnover thresholds)</li>
 *   <li>Filing deadline calculation</li>
 *   <li>Reverse charge applicability rules</li>
 *   <li>ViDA readiness flag (Phase 2)</li>
 *   <li>Authority name (for audit and reporting output)</li>
 * </ul>
 *
 * <p>The core tax-engine and persistence modules depend only on this interface.
 * Concrete implementations live in jurisdiction-specific sub-packages.
 *
 * <p>Registration: plugins are registered at application startup in the API module via a
 * {@code JurisdictionRegistry}. All business logic resolves the plugin by
 * {@link JurisdictionCode} and operates on this interface — never on concrete types.
 *
 * @see com.netcompany.vat.domain.dk.DkJurisdictionPlugin
 */
public interface JurisdictionPlugin {

    /**
     * The jurisdiction code this plugin implements.
     * Used by the registry to look up the correct plugin at runtime.
     */
    JurisdictionCode getCode();

    /**
     * Returns the VAT rate in basis points for the given tax code as of the given date.
     *
     * <p>Basis points: 2500 = 25.00%, 0 = 0%, -1 = not applicable (exempt).
     *
     * @param taxCode       the VAT treatment category
     * @param effectiveDate the date on which the rate must be valid (for historical correctness)
     * @return the rate in basis points, or -1 if the tax code is not applicable in this jurisdiction
     */
    long getVatRateInBasisPoints(TaxCode taxCode, LocalDate effectiveDate);

    /**
     * Determines the filing cadence for a taxpayer based on their annual turnover.
     *
     * @param annualTurnover annual turnover in the smallest currency unit (øre for DKK)
     * @return the applicable filing frequency
     */
    FilingCadence determineFilingCadence(MonetaryAmount annualTurnover);

    /**
     * Calculates the statutory filing deadline for the given period.
     *
     * @param period the tax period for which the deadline is needed
     * @return the last date on which a filing for this period is accepted without penalty
     */
    LocalDate calculateFilingDeadline(TaxPeriod period);

    /**
     * Determines whether reverse charge applies to the given transaction.
     *
     * <p>Reverse charge shifts VAT accounting from the supplier to the buyer.
     * In Denmark it applies to cross-border B2B services where the buyer is VAT-registered.
     *
     * @param transaction the transaction to evaluate
     * @return true if the buyer should self-assess VAT on this transaction
     */
    boolean isReverseChargeApplicable(Transaction transaction);

    /**
     * Returns true if this jurisdiction has active ViDA (VAT in the Digital Age) obligations.
     *
     * <p>Phase 2 feature: when true, the system routes transactions through the
     * Digital Reporting Requirements (DRR) pipeline before acknowledging them.
     */
    boolean isVidaEnabled();

    /**
     * The human-readable name of the tax authority for this jurisdiction.
     * Used in audit log entries and report headers.
     *
     * @return e.g. "SKAT" for Denmark, "Skatteetaten" for Norway
     */
    String getAuthorityName();
}
