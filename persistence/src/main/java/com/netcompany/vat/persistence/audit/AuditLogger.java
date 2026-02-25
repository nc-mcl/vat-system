package com.netcompany.vat.persistence.audit;

import java.util.List;
import java.util.UUID;

/**
 * Appends events to the immutable {@code audit_log} table.
 *
 * <p>Implementations must be synchronous: {@link #log} must complete (and the row must be
 * committed) before the caller proceeds with the state-changing operation it is auditing.
 */
public interface AuditLogger {

    /**
     * Appends an event to the audit log.
     * This method must be called before every state transition.
     *
     * @param event the audit event to record
     */
    void log(AuditEvent event);

    /**
     * Retrieves all events for a specific aggregate in insertion order.
     *
     * @param aggregateType e.g. "VatReturn"
     * @param aggregateId   the primary key of the aggregate
     */
    List<AuditEvent> findByAggregate(String aggregateType, UUID aggregateId);
}
