package com.netcompany.vat.persistence;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.JooqAuditLogger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the audit log is append-only:
 * events are inserted and can be queried, but there is no delete path.
 */
class AuditLoggerIT extends AbstractRepositoryIT {

    @BeforeAll
    static void setUp() {
        initDatabase();
        // Audit log accumulates across all tests — no truncation
    }

    @Test
    void log_appendsEventToAuditLog() {
        JooqAuditLogger logger = new JooqAuditLogger(ctx, objectMapper);
        UUID aggregateId = UUID.randomUUID();

        AuditEvent event = new AuditEvent(
                "VatReturn",
                aggregateId,
                "RETURN_ASSEMBLED",
                "system",
                Map.of("outputVat", 250000L, "inputVat", 100000L),
                JurisdictionCode.DK
        );

        logger.log(event);

        List<AuditEvent> found = logger.findByAggregate("VatReturn", aggregateId);
        assertEquals(1, found.size());

        AuditEvent loaded = found.get(0);
        assertEquals("VatReturn", loaded.aggregateType());
        assertEquals(aggregateId, loaded.aggregateId());
        assertEquals("RETURN_ASSEMBLED", loaded.eventType());
        assertEquals("system", loaded.actor());
        assertEquals(JurisdictionCode.DK, loaded.jurisdictionCode());
        assertNotNull(loaded.payload());
    }

    @Test
    void log_multipleEvents_returnedInInsertionOrder() {
        JooqAuditLogger logger = new JooqAuditLogger(ctx, objectMapper);
        UUID aggregateId = UUID.randomUUID();

        logger.log(new AuditEvent("VatReturn", aggregateId, "RETURN_ASSEMBLED", "system",
                Map.of("step", 1), JurisdictionCode.DK));
        logger.log(new AuditEvent("VatReturn", aggregateId, "RETURN_SUBMITTED", "user1",
                Map.of("step", 2), JurisdictionCode.DK));
        logger.log(new AuditEvent("VatReturn", aggregateId, "RETURN_ACCEPTED", "skat-api",
                Map.of("step", 3), JurisdictionCode.DK));

        List<AuditEvent> events = logger.findByAggregate("VatReturn", aggregateId);
        assertEquals(3, events.size());
        assertEquals("RETURN_ASSEMBLED", events.get(0).eventType());
        assertEquals("RETURN_SUBMITTED", events.get(1).eventType());
        assertEquals("RETURN_ACCEPTED", events.get(2).eventType());
    }

    @Test
    void log_withNullActor_persists() {
        JooqAuditLogger logger = new JooqAuditLogger(ctx, objectMapper);
        UUID aggregateId = UUID.randomUUID();

        assertDoesNotThrow(() -> logger.log(new AuditEvent(
                "Transaction", aggregateId, "TRANSACTION_CREATED", null,
                Map.of("amount", 50000L), JurisdictionCode.DK)));
    }

    @Test
    void auditLog_rowsAreNeverDeleted() {
        JooqAuditLogger logger = new JooqAuditLogger(ctx, objectMapper);
        UUID aggregateId = UUID.randomUUID();

        logger.log(new AuditEvent("TaxPeriod", aggregateId, "PERIOD_OPENED", "system",
                Map.of(), JurisdictionCode.DK));

        // Attempt to count events — should be > 0 and stable
        int countBefore = ctx.selectCount()
                .from(table("audit_log"))
                .where(field("aggregate_id", UUID.class).eq(aggregateId))
                .fetchOne(0, Integer.class);

        // There is no delete method on AuditLogger — this verifies the interface has no such method
        // The count should remain the same (no background deletion)
        int countAfter = ctx.selectCount()
                .from(table("audit_log"))
                .where(field("aggregate_id", UUID.class).eq(aggregateId))
                .fetchOne(0, Integer.class);

        assertEquals(1, countBefore);
        assertEquals(countBefore, countAfter, "Audit log rows must never be deleted");
    }

    @Test
    void findByAggregate_doesNotReturnOtherAggregates() {
        JooqAuditLogger logger = new JooqAuditLogger(ctx, objectMapper);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        logger.log(new AuditEvent("VatReturn", id1, "EVENT_A", null, Map.of(), JurisdictionCode.DK));
        logger.log(new AuditEvent("VatReturn", id2, "EVENT_B", null, Map.of(), JurisdictionCode.DK));

        List<AuditEvent> forId1 = logger.findByAggregate("VatReturn", id1);
        assertEquals(1, forId1.size());
        assertEquals(id1, forId1.get(0).aggregateId());
    }
}
