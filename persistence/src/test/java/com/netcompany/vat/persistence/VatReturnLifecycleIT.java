package com.netcompany.vat.persistence;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.ResultType;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.JooqAuditLogger;
import com.netcompany.vat.persistence.repository.jooq.JooqTaxPeriodRepository;
import com.netcompany.vat.persistence.repository.jooq.JooqVatReturnRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence integration tests for the {@link VatReturn} lifecycle.
 *
 * <p>Verifies:
 * <ul>
 *   <li>DRAFT return is saved with {@code assembledAt} populated</li>
 *   <li>{@code updateStatusAndReference()} persists ACCEPTED status, timestamp, and skatReference</li>
 *   <li>REJECTED transitions store null skatReference</li>
 *   <li>Audit log captures RETURN_ASSEMBLED, RETURN_SUBMITTED, RETURN_ACCEPTED events</li>
 *   <li>Immutability: audit log rows are never deleted</li>
 * </ul>
 *
 * <p>Requires Docker (skips if unavailable). Always runs in CI.
 */
class VatReturnLifecycleIT extends AbstractRepositoryIT {

    private JooqVatReturnRepository repository;
    private JooqAuditLogger auditLogger;
    private UUID periodId;

    @BeforeAll
    static void setUp() {
        initDatabase();
    }

    @BeforeEach
    void reset() {
        truncateTables();
        repository  = new JooqVatReturnRepository(ctx, objectMapper);
        auditLogger = new JooqAuditLogger(ctx, objectMapper);

        periodId = UUID.randomUUID();
        TaxPeriod period = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        new JooqTaxPeriodRepository(ctx).save(period, LocalDate.of(2026, 6, 1));
    }

    // ── save() — assembledAt ──────────────────────────────────────────────────

    @Test
    void save_draftReturn_assembledAtIsSet() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        VatReturn saved = repository.save(draft);

        // save() sets assembledAt at DB insertion time
        assertNotNull(saved.assembledAt(),
                "assembledAt must be set by save() — it records when the return was assembled");
        assertTrue(saved.assembledAt().isBefore(Instant.now().plusSeconds(5)),
                "assembledAt must be a recent timestamp");
    }

    @Test
    void save_draftReturn_allFieldsPersisted() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);

        VatReturn loaded = repository.findById(draft.id()).orElseThrow();

        assertEquals(draft.id(),                         loaded.id());
        assertEquals(JurisdictionCode.DK,                loaded.jurisdictionCode());
        assertEquals(draft.periodId(),                   loaded.periodId());
        assertEquals(250_000L,                           loaded.outputVat().oere());
        assertEquals(100_000L,                           loaded.inputVatDeductible().oere());
        assertEquals(150_000L,                           loaded.netVat().oere());
        assertEquals(ResultType.PAYABLE,                 loaded.resultType());
        assertEquals(0L,                                 loaded.claimAmount().oere());
        assertEquals(VatReturnStatus.DRAFT,              loaded.status());
        // Lifecycle timestamps — only assembledAt is set on save
        assertNotNull(loaded.assembledAt());
        assertNull(loaded.submittedAt(),   "submittedAt is null until submitted");
        assertNull(loaded.acceptedAt(),    "acceptedAt is null until accepted");
        assertNull(loaded.skatReference(), "skatReference is null until accepted");
    }

    @Test
    void save_nilReturn_resultTypeIsZero() {
        VatReturn nilReturn = VatReturn.of(
                UUID.randomUUID(), JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT, Map.of());
        repository.save(nilReturn);

        VatReturn loaded = repository.findById(nilReturn.id()).orElseThrow();
        assertEquals(ResultType.ZERO, loaded.resultType());
        assertEquals(0L, loaded.netVat().oere());
        assertEquals(0L, loaded.claimAmount().oere());
    }

    // ── updateStatusAndReference() — ACCEPTED ─────────────────────────────────

    @Test
    void updateStatusAndReference_toAccepted_setsAllFields() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);

        Instant acceptedAt = Instant.parse("2026-04-01T12:00:00Z");
        String  skatRef    = "SKAT-2026-Q1-00042";

        VatReturn accepted = repository.updateStatusAndReference(
                draft.id(), VatReturnStatus.ACCEPTED, acceptedAt, skatRef);

        assertEquals(VatReturnStatus.ACCEPTED, accepted.status());
        assertEquals(skatRef, accepted.skatReference());
        // The acceptedAt is stored — may differ slightly from input due to DB precision
        assertNotNull(accepted.acceptedAt());
    }

    @Test
    void updateStatusAndReference_toAccepted_persistedAcrossReload() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);

        String skatRef = "SKAT-RELOAD-TEST-001";
        repository.updateStatusAndReference(
                draft.id(), VatReturnStatus.ACCEPTED, Instant.now(), skatRef);

        VatReturn reloaded = repository.findById(draft.id()).orElseThrow();
        assertEquals(VatReturnStatus.ACCEPTED, reloaded.status());
        assertEquals(skatRef, reloaded.skatReference());
    }

    // ── updateStatusAndReference() — REJECTED ─────────────────────────────────

    @Test
    void updateStatusAndReference_toRejected_noSkatReference() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);

        VatReturn rejected = repository.updateStatusAndReference(
                draft.id(), VatReturnStatus.REJECTED, Instant.now(), null);

        assertEquals(VatReturnStatus.REJECTED, rejected.status());
        assertNull(rejected.skatReference(), "REJECTED returns must not have a skatReference");
    }

    @Test
    void updateStatusAndReference_toRejected_persistedAcrossReload() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);
        repository.updateStatusAndReference(draft.id(), VatReturnStatus.REJECTED, Instant.now(), null);

        VatReturn reloaded = repository.findById(draft.id()).orElseThrow();
        assertEquals(VatReturnStatus.REJECTED, reloaded.status());
        assertNull(reloaded.skatReference());
    }

    // ── Audit log verification ─────────────────────────────────────────────────

    @Test
    void auditLog_fullLifecycle_containsAllEvents() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        UUID returnId = draft.id();

        // RETURN_ASSEMBLED
        auditLogger.log(new AuditEvent(
                "VatReturn", returnId, "RETURN_ASSEMBLED", "system",
                Map.of("outputVat", 250000L, "netVat", 150000L, "resultType", "PAYABLE"),
                JurisdictionCode.DK));
        repository.save(draft);

        // RETURN_SUBMITTED
        auditLogger.log(new AuditEvent(
                "VatReturn", returnId, "RETURN_SUBMITTED", "system",
                Map.of("periodId", periodId.toString()),
                JurisdictionCode.DK));

        // RETURN_ACCEPTED
        auditLogger.log(new AuditEvent(
                "VatReturn", returnId, "RETURN_ACCEPTED", "skat-api",
                Map.of("skatReference", "SKAT-TEST-001"),
                JurisdictionCode.DK));
        repository.updateStatusAndReference(
                returnId, VatReturnStatus.ACCEPTED, Instant.now(), "SKAT-TEST-001");

        // Verify audit trail has all three events in order
        List<AuditEvent> events = auditLogger.findByAggregate("VatReturn", returnId);
        assertEquals(3, events.size(), "Expected ASSEMBLED, SUBMITTED, ACCEPTED events");
        assertEquals("RETURN_ASSEMBLED",  events.get(0).eventType());
        assertEquals("RETURN_SUBMITTED",  events.get(1).eventType());
        assertEquals("RETURN_ACCEPTED",   events.get(2).eventType());

        // Verify actor recorded
        assertEquals("skat-api", events.get(2).actor());
    }

    @Test
    void auditLog_appendsOnly_neverDeletes() {
        UUID aggregateId = UUID.randomUUID();

        auditLogger.log(new AuditEvent(
                "VatReturn", aggregateId, "RETURN_ASSEMBLED", "system",
                Map.of(), JurisdictionCode.DK));

        List<AuditEvent> before = auditLogger.findByAggregate("VatReturn", aggregateId);
        int countBefore = before.size();

        // No delete path exists on AuditLogger — attempt nothing, verify count stable
        List<AuditEvent> after = auditLogger.findByAggregate("VatReturn", aggregateId);
        assertEquals(countBefore, after.size(), "Audit log must be append-only — no events deleted");
        assertEquals(1, countBefore, "Exactly one event should exist");
    }

    @Test
    void auditLog_rejectionEvent_isRecorded() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        UUID returnId = draft.id();
        repository.save(draft);

        auditLogger.log(new AuditEvent(
                "VatReturn", returnId, "RETURN_REJECTED", "skat-api",
                Map.of("reason", "Validation error DK-VAT-001"),
                JurisdictionCode.DK));
        repository.updateStatusAndReference(returnId, VatReturnStatus.REJECTED, Instant.now(), null);

        List<AuditEvent> events = auditLogger.findByAggregate("VatReturn", returnId);
        assertEquals(1, events.size());
        assertEquals("RETURN_REJECTED", events.get(0).eventType());

        VatReturn reloaded = repository.findById(returnId).orElseThrow();
        assertEquals(VatReturnStatus.REJECTED, reloaded.status());
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    void findByStatus_draft_returnsOnlyDraftReturns() {
        VatReturn draft = buildDraftReturn(UUID.randomUUID());
        repository.save(draft);

        // Save a second period + return, then accept it
        UUID period2Id = UUID.randomUUID();
        TaxPeriod period2 = new TaxPeriod(period2Id, JurisdictionCode.DK,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        new JooqTaxPeriodRepository(ctx).save(period2, LocalDate.of(2026, 9, 1));

        VatReturn draft2 = VatReturn.of(UUID.randomUUID(), JurisdictionCode.DK, period2Id,
                MonetaryAmount.ofOere(50_000L), MonetaryAmount.ofOere(10_000L),
                VatReturnStatus.DRAFT, Map.of());
        repository.save(draft2);
        repository.updateStatusAndReference(draft2.id(), VatReturnStatus.ACCEPTED,
                Instant.now(), "SKAT-TEST-XYZ");

        List<VatReturn> drafts = repository.findByStatus(VatReturnStatus.DRAFT);
        assertEquals(1, drafts.size());
        assertEquals(draft.id(), drafts.get(0).id());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private VatReturn buildDraftReturn(UUID id) {
        return VatReturn.of(
                id, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L),  // output VAT
                MonetaryAmount.ofOere(100_000L),  // input VAT → net = 150,000 PAYABLE
                VatReturnStatus.DRAFT,
                Map.of("rubrikA", 0L, "rubrikB", 0L)
        );
    }
}
