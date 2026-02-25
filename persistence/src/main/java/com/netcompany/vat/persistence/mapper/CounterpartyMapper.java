package com.netcompany.vat.persistence.mapper;

import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.JurisdictionCode;
import org.jooq.Record;

import java.util.UUID;

/** Pure-function mapper for the {@code counterparties} table. */
public final class CounterpartyMapper {

    private CounterpartyMapper() {}

    public static Counterparty fromRecord(Record r) {
        return new Counterparty(
                r.get("id", UUID.class),
                JurisdictionCode.fromString(r.get("jurisdiction_code", String.class)),
                r.get("vat_number", String.class),
                r.get("name", String.class)
        );
    }
}
