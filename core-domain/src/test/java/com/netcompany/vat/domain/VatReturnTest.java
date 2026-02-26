package com.netcompany.vat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VatReturn} derivation logic.
 *
 * <p>Verifies that {@code netVat}, {@code resultType}, and {@code claimAmount} are
 * correctly derived from {@code outputVat} and {@code inputVatDeductible} at construction time.
 *
 * <p>Rule 7.8 (dk-vat-rules-validated.md):
 * <pre>
 *   Net VAT = outputVat − inputVatDeductible
 *   PAYABLE  : netVat > 0
 *   CLAIMABLE: netVat < 0
 *   ZERO     : netVat == 0
 * </pre>
 */
@DisplayName("VatReturn")
class VatReturnTest {

    private static final UUID ID         = UUID.randomUUID();
    private static final UUID PERIOD_ID  = UUID.randomUUID();
    private static final JurisdictionCode CODE = JurisdictionCode.DK;

    // ── netVat derivation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("netVat = outputVat - inputVatDeductible (basic derivation)")
    void netVat_derivedAsOutputMinusInput() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(500_000L),  // output
                MonetaryAmount.ofOere(200_000L),  // input
                VatReturnStatus.DRAFT,
                Map.of());

        assertEquals(300_000L, vr.netVat().oere());
    }

    @Test
    @DisplayName("netVat = 0 when output equals input")
    void netVat_zeroWhenOutputEqualsInput() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(250_000L),
                MonetaryAmount.ofOere(250_000L),
                VatReturnStatus.DRAFT,
                Map.of());

        assertEquals(0L, vr.netVat().oere());
    }

    @Test
    @DisplayName("netVat is negative when input exceeds output (claimable scenario)")
    void netVat_negativeWhenInputExceedsOutput() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),  // output
                MonetaryAmount.ofOere(300_000L),  // input > output
                VatReturnStatus.DRAFT,
                Map.of());

        assertEquals(-200_000L, vr.netVat().oere());
    }

    // ── resultType PAYABLE ────────────────────────────────────────────────────

    @Test
    @DisplayName("resultType is PAYABLE when netVat > 0")
    void resultType_PAYABLE_whenNetVatPositive() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(500_000L),
                MonetaryAmount.ofOere(200_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(ResultType.PAYABLE, vr.resultType());
    }

    @Test
    @DisplayName("PAYABLE: claimAmount is ZERO (no refund due)")
    void payable_claimAmountIsZero() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(500_000L),
                MonetaryAmount.ofOere(200_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(MonetaryAmount.ZERO, vr.claimAmount());
        assertEquals(0L, vr.claimAmount().oere());
    }

    // ── resultType CLAIMABLE ──────────────────────────────────────────────────

    @Test
    @DisplayName("resultType is CLAIMABLE when netVat < 0")
    void resultType_CLAIMABLE_whenNetVatNegative() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(300_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(ResultType.CLAIMABLE, vr.resultType());
    }

    @Test
    @DisplayName("CLAIMABLE: claimAmount is absolute value of netVat")
    void claimable_claimAmountIsAbsoluteNetVat() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(300_000L),
                VatReturnStatus.DRAFT, Map.of());

        // netVat = -200,000 → claimAmount = 200,000
        assertEquals(200_000L, vr.claimAmount().oere());
        assertTrue(vr.claimAmount().isPositive(), "claimAmount must be positive");
    }

    // ── resultType ZERO (nulindberetning) ────────────────────────────────────

    @Test
    @DisplayName("resultType is ZERO when netVat == 0")
    void resultType_ZERO_whenNetVatZero() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(250_000L),
                MonetaryAmount.ofOere(250_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(ResultType.ZERO, vr.resultType());
    }

    @Test
    @DisplayName("ZERO: claimAmount is zero (no refund)")
    void zero_claimAmountIsZero() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(0L, vr.claimAmount().oere());
    }

    @Test
    @DisplayName("empty period (nulindberetning): outputVat=0, inputVat=0 → ZERO")
    void emptyPeriod_nilReturn_isZero() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(0L, vr.netVat().oere());
        assertEquals(ResultType.ZERO, vr.resultType());
        assertEquals(0L, vr.claimAmount().oere());
    }

    // ── Lifecycle fields — nullable ───────────────────────────────────────────

    @Test
    @DisplayName("7-param factory leaves all lifecycle fields null")
    void shortFactory_setsLifecycleFieldsNull() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(50_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertNull(vr.assembledAt(),   "assembledAt should be null until assembled");
        assertNull(vr.submittedAt(),   "submittedAt should be null until submitted");
        assertNull(vr.acceptedAt(),    "acceptedAt should be null until accepted");
        assertNull(vr.skatReference(), "skatReference should be null until accepted");
    }

    @Test
    @DisplayName("11-param factory sets lifecycle fields when provided")
    void fullFactory_setsLifecycleFields() {
        Instant assembled  = Instant.parse("2026-04-01T10:00:00Z");
        Instant submitted  = Instant.parse("2026-04-01T10:01:00Z");
        Instant accepted   = Instant.parse("2026-04-01T10:02:00Z");
        String  reference  = "SKAT-2026-Q1-001";

        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(50_000L),
                VatReturnStatus.ACCEPTED,
                Map.of(),
                assembled, submitted, accepted, reference);

        assertEquals(assembled, vr.assembledAt());
        assertEquals(submitted, vr.submittedAt());
        assertEquals(accepted,  vr.acceptedAt());
        assertEquals(reference, vr.skatReference());
    }

    @Test
    @DisplayName("lifecycle fields are individually nullable in 11-param factory")
    void fullFactory_lifecycleFields_partiallyNullable() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(50_000L),
                VatReturnStatus.DRAFT,
                Map.of(),
                Instant.now(), null, null, null);

        assertNotNull(vr.assembledAt());
        assertNull(vr.submittedAt());
        assertNull(vr.acceptedAt());
        assertNull(vr.skatReference());
    }

    @Test
    @DisplayName("14-arg direct constructor also works with nulls for lifecycle fields")
    void directConstructor_nullLifecycleFields() {
        VatReturn vr = new VatReturn(
                ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(500_000L),
                MonetaryAmount.ofOere(200_000L),
                MonetaryAmount.ofOere(300_000L),
                ResultType.PAYABLE,
                MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT,
                Map.of(),
                null, null, null, null
        );

        assertEquals(300_000L, vr.netVat().oere());
        assertEquals(ResultType.PAYABLE, vr.resultType());
        assertNull(vr.assembledAt());
        assertNull(vr.skatReference());
    }

    // ── Identity and structural fields ────────────────────────────────────────

    @Test
    @DisplayName("VatReturn carries correct identifiers")
    void identifiers_arePreserved() {
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ofOere(50_000L),
                VatReturnStatus.DRAFT, Map.of());

        assertEquals(ID,         vr.id());
        assertEquals(CODE,       vr.jurisdictionCode());
        assertEquals(PERIOD_ID,  vr.periodId());
        assertEquals(VatReturnStatus.DRAFT, vr.status());
    }

    @Test
    @DisplayName("jurisdictionFields map is stored and accessible")
    void jurisdictionFields_arePreserved() {
        Map<String, Object> fields = Map.of("rubrikA", 500_000L, "rubrikB", 300_000L);
        VatReturn vr = VatReturn.of(ID, CODE, PERIOD_ID,
                MonetaryAmount.ofOere(100_000L),
                MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT, fields);

        assertEquals(500_000L, vr.jurisdictionFields().get("rubrikA"));
        assertEquals(300_000L, vr.jurisdictionFields().get("rubrikB"));
    }
}
