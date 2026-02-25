package com.netcompany.vat.persistence.mapper;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
import org.jooq.Record;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pure-function mapper between JOOQ rows from {@code transactions} and domain {@link Transaction}.
 */
public final class TransactionMapper {

    private TransactionMapper() {}

    /**
     * Maps a JOOQ row to a domain {@link Transaction}.
     *
     * <p>The {@link TaxClassification} is reconstructed from the four stored columns:
     * {@code tax_code}, {@code rate_basis_points}, {@code is_reverse_charge},
     * and {@code classification_date}.
     */
    public static Transaction fromRecord(Record r) {
        TaxClassification classification = new TaxClassification(
                TaxCode.valueOf(r.get("tax_code", String.class)),
                r.get("rate_basis_points", Integer.class).longValue(),
                r.get("is_reverse_charge", Boolean.class),
                r.get("classification_date", LocalDate.class)
        );

        OffsetDateTime createdAt = r.get("created_at", OffsetDateTime.class);

        return new Transaction(
                r.get("id", UUID.class),
                JurisdictionCode.fromString(r.get("jurisdiction_code", String.class)),
                r.get("period_id", UUID.class),
                r.get("counterparty_id", UUID.class),
                r.get("transaction_date", LocalDate.class),
                r.get("description", String.class),
                MonetaryAmount.ofOere(r.get("amount_excl_vat", Long.class)),
                classification,
                createdAt != null ? createdAt.toInstant() : null
        );
    }
}
