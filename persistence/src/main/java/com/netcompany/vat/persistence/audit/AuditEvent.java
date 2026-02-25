package com.netcompany.vat.persistence.audit;

import com.netcompany.vat.domain.JurisdictionCode;

import java.util.Map;
import java.util.UUID;

/**
 * An audit event to be appended to the immutable {@code audit_log} table.
 *
 * <p>Callers must create and log an event <em>before</em> every state-changing
 * operation (Audit First principle).
 *
 * @param aggregateType    entity class name, e.g. "VatReturn"
 * @param aggregateId      the primary key of the affected entity
 * @param eventType        what happened, e.g. "RETURN_ASSEMBLED", "TRANSACTION_CREATED"
 * @param actor            system or user identity (may be null for automated events)
 * @param payload          full snapshot of the entity at the time of the event
 * @param jurisdictionCode jurisdiction context for partitioning and filtering
 */
public record AuditEvent(
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String actor,
        Map<String, Object> payload,
        JurisdictionCode jurisdictionCode
) {}
