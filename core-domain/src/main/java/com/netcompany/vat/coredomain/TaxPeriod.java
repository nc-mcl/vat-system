package com.netcompany.vat.coredomain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A time window for which a taxpayer must file a VAT return.
 *
 * <p>The period is jurisdiction-scoped. Its cadence and status are managed
 * by the system; the filing deadline is computed by the jurisdiction plugin.
 *
 * @param id               immutable system identifier
 * @param jurisdictionCode the jurisdiction this period belongs to
 * @param startDate        first day of the period (inclusive)
 * @param endDate          last day of the period (inclusive)
 * @param cadence          how frequently the taxpayer must file
 * @param status           current lifecycle state of the period
 */
public record TaxPeriod(
        UUID id,
        JurisdictionCode jurisdictionCode,
        LocalDate startDate,
        LocalDate endDate,
        FilingCadence cadence,
        TaxPeriodStatus status
) {

    /**
     * Returns the number of days in this period (inclusive of both start and end).
     * For example, Jan 1 – Jan 31 = 31 days.
     */
    public long periodDays() {
        return startDate.until(endDate).getDays() + 1;
    }

    /** Returns true if this period is still open for transaction assignment. */
    public boolean isOpen() {
        return status == TaxPeriodStatus.OPEN;
    }
}
