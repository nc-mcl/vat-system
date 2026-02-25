package com.netcompany.vat.persistence.repository.jooq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.persistence.mapper.VatReturnMapper;
import com.netcompany.vat.persistence.repository.VatReturnRepository;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqVatReturnRepository implements VatReturnRepository {

    private final DSLContext ctx;
    private final ObjectMapper objectMapper;

    public JooqVatReturnRepository(DSLContext ctx, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
    }

    @Override
    public VatReturn save(VatReturn vatReturn) {
        Instant assembledAt = Instant.now();
        OffsetDateTime assembledAtOdt = assembledAt.atOffset(ZoneOffset.UTC);
        OffsetDateTime now = assembledAtOdt;
        JSONB jurisdictionFieldsJson = toJsonb(vatReturn.jurisdictionFields());

        long rubrikA = toLong(vatReturn.jurisdictionFields().get("rubrikA"));
        long rubrikB = toLong(vatReturn.jurisdictionFields().get("rubrikB"));

        ctx.insertInto(table("vat_returns"))
                .columns(
                        field("id"),
                        field("period_id"),
                        field("jurisdiction_code"),
                        field("output_vat"),
                        field("input_vat"),
                        field("net_vat"),
                        field("result_type"),
                        field("claim_amount"),
                        field("status"),
                        field("jurisdiction_fields"),
                        field("rubrik_a"),
                        field("rubrik_b"),
                        field("assembled_at"),
                        field("created_at"),
                        field("updated_at")
                )
                .values(
                        vatReturn.id(),
                        vatReturn.periodId(),
                        vatReturn.jurisdictionCode().name(),
                        vatReturn.outputVat().oere(),
                        vatReturn.inputVatDeductible().oere(),
                        vatReturn.netVat().oere(),
                        vatReturn.resultType().name(),
                        vatReturn.claimAmount().oere(),
                        vatReturn.status().name(),
                        jurisdictionFieldsJson,
                        rubrikA,
                        rubrikB,
                        assembledAtOdt,
                        now,
                        now
                )
                .execute();

        // Return a new record with assembledAt populated
        return VatReturn.of(
                vatReturn.id(),
                vatReturn.jurisdictionCode(),
                vatReturn.periodId(),
                vatReturn.outputVat(),
                vatReturn.inputVatDeductible(),
                vatReturn.status(),
                vatReturn.jurisdictionFields(),
                assembledAt,
                null,
                null,
                null
        );
    }

    @Override
    public Optional<VatReturn> findById(UUID id) {
        return ctx.selectFrom(table("vat_returns"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional(r -> VatReturnMapper.fromRecord(r, objectMapper));
    }

    @Override
    public Optional<VatReturn> findByPeriod(UUID periodId, JurisdictionCode code) {
        return ctx.selectFrom(table("vat_returns"))
                .where(field("period_id", UUID.class).eq(periodId))
                .and(field("jurisdiction_code", String.class).eq(code.name()))
                .fetchOptional(r -> VatReturnMapper.fromRecord(r, objectMapper));
    }

    @Override
    public List<VatReturn> findByStatus(VatReturnStatus status) {
        return ctx.selectFrom(table("vat_returns"))
                .where(field("status", String.class).eq(status.name()))
                .fetch(r -> VatReturnMapper.fromRecord(r, objectMapper));
    }

    @Override
    public VatReturn updateStatus(UUID id, VatReturnStatus status, Instant timestamp) {
        return updateStatusAndReference(id, status, timestamp, null);
    }

    @Override
    public VatReturn updateStatusAndReference(UUID id, VatReturnStatus status, Instant timestamp, String skatReference) {
        OffsetDateTime ts = timestamp != null ? timestamp.atOffset(ZoneOffset.UTC) : null;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ctx.update(table("vat_returns"))
                .set(field("status"), status.name())
                .set(field("updated_at"), now)
                .where(field("id", UUID.class).eq(id))
                .execute();

        if (status == VatReturnStatus.SUBMITTED && ts != null) {
            ctx.update(table("vat_returns"))
                    .set(field("submitted_at"), ts)
                    .where(field("id", UUID.class).eq(id))
                    .execute();
        } else if (status == VatReturnStatus.ACCEPTED && ts != null) {
            ctx.update(table("vat_returns"))
                    .set(field("accepted_at"), ts)
                    .set(field("skat_reference"), skatReference)
                    .where(field("id", UUID.class).eq(id))
                    .execute();
        } else if (status == VatReturnStatus.REJECTED && ts != null) {
            ctx.update(table("vat_returns"))
                    .set(field("accepted_at"), ts)
                    .where(field("id", UUID.class).eq(id))
                    .execute();
        }

        return findById(id)
                .orElseThrow(() -> new IllegalStateException("VatReturn not found after status update: " + id));
    }

    private JSONB toJsonb(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return JSONB.jsonb("{}");
        }
        try {
            return JSONB.jsonb(objectMapper.writeValueAsString(fields));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise jurisdiction_fields", e);
        }
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
