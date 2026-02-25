package com.netcompany.vat.persistence.mapper;

import com.netcompany.vat.domain.Correction;
import org.jooq.Record;

import java.util.UUID;

/** Pure-function mapper for the {@code corrections} table. */
public final class CorrectionMapper {

    private CorrectionMapper() {}

    public static Correction fromRecord(Record r) {
        return new Correction(
                r.get("id", UUID.class),
                r.get("original_return_id", UUID.class),
                r.get("corrected_return_id", UUID.class),
                r.get("reason", String.class)
        );
    }
}
