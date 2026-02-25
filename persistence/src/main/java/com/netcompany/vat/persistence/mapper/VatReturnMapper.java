package com.netcompany.vat.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import org.jooq.Record;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Pure-function mapper between JOOQ rows from {@code vat_returns} and domain {@link VatReturn}.
 *
 * <p>Requires a shared {@link ObjectMapper} for JSONB deserialization of
 * {@code jurisdiction_fields}.  The caller should supply a properly configured instance
 * (e.g. from the Spring application context).
 */
public final class VatReturnMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};

    private VatReturnMapper() {}

    /**
     * Maps a JOOQ row to a domain {@link VatReturn}.
     *
     * <p>Uses {@link VatReturn#of} so that {@code netVat}, {@code resultType},
     * and {@code claimAmount} are recomputed from their stored source values
     * rather than read blindly from DB columns.
     */
    public static VatReturn fromRecord(Record r, ObjectMapper objectMapper) {
        MonetaryAmount outputVat = MonetaryAmount.ofOere(r.get("output_vat", Long.class));
        MonetaryAmount inputVat  = MonetaryAmount.ofOere(r.get("input_vat", Long.class));

        Object rawJson = r.get("jurisdiction_fields");
        Map<String, Object> fields = parseJurisdictionFields(rawJson, objectMapper);

        return VatReturn.of(
                r.get("id", UUID.class),
                JurisdictionCode.fromString(r.get("jurisdiction_code", String.class)),
                r.get("period_id", UUID.class),
                outputVat,
                inputVat,
                VatReturnStatus.valueOf(r.get("status", String.class)),
                fields
        );
    }

    private static Map<String, Object> parseJurisdictionFields(Object raw, ObjectMapper mapper) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        String json = raw.toString();
        if (json.isBlank() || "null".equalsIgnoreCase(json)) {
            return Collections.emptyMap();
        }
        try {
            return mapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise jurisdiction_fields: " + json, e);
        }
    }
}
