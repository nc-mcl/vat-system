package com.netcompany.vat.persistence.mapper;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import org.jooq.Record;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Pure-function mapper between JOOQ {@link Record} rows from {@code tax_periods}
 * and the domain {@link TaxPeriod} record.
 *
 * <p>No Spring dependencies — safe to use in tests without a Spring context.
 */
public final class TaxPeriodMapper {

    private TaxPeriodMapper() {}

    /**
     * Maps a JOOQ row from {@code tax_periods} to a domain {@link TaxPeriod}.
     *
     * <p>Note: {@code filing_deadline} is stored in the DB for query convenience
     * but is not part of the domain record (it is derived by the jurisdiction plugin).
     */
    public static TaxPeriod fromRecord(Record r) {
        return new TaxPeriod(
                r.get("id", UUID.class),
                JurisdictionCode.fromString(r.get("jurisdiction_code", String.class)),
                r.get("period_start", LocalDate.class),
                r.get("period_end", LocalDate.class),
                FilingCadence.valueOf(r.get("filing_cadence", String.class)),
                TaxPeriodStatus.valueOf(r.get("status", String.class))
        );
    }
}
