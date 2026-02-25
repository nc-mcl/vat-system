package com.netcompany.vat.persistence.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcompany.vat.domain.JurisdictionCode;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * JOOQ-backed implementation of {@link AuditLogger}.
 *
 * <p>Appends to the {@code audit_log} table which is append-only by design
 * (Bogføringsloven compliance).  No DELETE path exists in this class.
 */
@Component
public class JooqAuditLogger implements AuditLogger {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final DSLContext ctx;
    private final ObjectMapper objectMapper;

    public JooqAuditLogger(DSLContext ctx, ObjectMapper objectMapper) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
    }

    @Override
    public void log(AuditEvent event) {
        ctx.insertInto(table("audit_log"))
                .columns(
                        field("aggregate_type"),
                        field("aggregate_id"),
                        field("event_type"),
                        field("actor"),
                        field("payload"),
                        field("jurisdiction_code"),
                        field("event_time")
                )
                .values(
                        event.aggregateType(),
                        event.aggregateId(),
                        event.eventType(),
                        event.actor(),
                        toJsonb(event.payload()),
                        event.jurisdictionCode().name(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
                .execute();
    }

    @Override
    public List<AuditEvent> findByAggregate(String aggregateType, UUID aggregateId) {
        return ctx.selectFrom(table("audit_log"))
                .where(field("aggregate_type", String.class).eq(aggregateType))
                .and(field("aggregate_id", UUID.class).eq(aggregateId))
                .orderBy(field("id"))
                .fetch(r -> {
                    Object rawPayload = r.get("payload");
                    Map<String, Object> payload = parsePayload(rawPayload);
                    return new AuditEvent(
                            r.get("aggregate_type", String.class),
                            r.get("aggregate_id", UUID.class),
                            r.get("event_type", String.class),
                            r.get("actor", String.class),
                            payload,
                            JurisdictionCode.fromString(r.get("jurisdiction_code", String.class))
                    );
                });
    }

    private JSONB toJsonb(Map<String, Object> payload) {
        try {
            return JSONB.jsonb(objectMapper.writeValueAsString(
                    payload != null ? payload : Collections.emptyMap()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise audit payload", e);
        }
    }

    private Map<String, Object> parsePayload(Object raw) {
        if (raw == null) return Collections.emptyMap();
        String json = raw.toString();
        if (json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise audit payload: " + json, e);
        }
    }
}
