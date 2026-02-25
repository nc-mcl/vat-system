package com.netcompany.vat.persistence;

import com.netcompany.vat.domain.*;
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

class VatReturnRepositoryIT extends AbstractRepositoryIT {

    private JooqVatReturnRepository repository;
    private UUID periodId;

    @BeforeAll
    static void setUp() {
        initDatabase();
    }

    @BeforeEach
    void reset() {
        truncateTables();
        repository = new JooqVatReturnRepository(ctx, objectMapper);

        periodId = UUID.randomUUID();
        TaxPeriod period = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        new JooqTaxPeriodRepository(ctx).save(period, LocalDate.of(2026, 6, 1));
    }

    private VatReturn draftReturn(UUID id) {
        return VatReturn.of(
                id, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L),  // output VAT
                MonetaryAmount.ofOere(100_000L),  // input VAT
                VatReturnStatus.DRAFT,
                Map.of("rubrikA", 500_000L, "rubrikB", 800_000L)
        );
    }

    @Test
    void save_and_findById_roundTrip() {
        VatReturn vatReturn = draftReturn(UUID.randomUUID());
        repository.save(vatReturn);

        Optional<VatReturn> found = repository.findById(vatReturn.id());
        assertTrue(found.isPresent());
        VatReturn loaded = found.get();

        assertEquals(vatReturn.id(), loaded.id());
        assertEquals(JurisdictionCode.DK, loaded.jurisdictionCode());
        assertEquals(250_000L, loaded.outputVat().oere());
        assertEquals(100_000L, loaded.inputVatDeductible().oere());
        assertEquals(150_000L, loaded.netVat().oere());
        assertEquals(ResultType.PAYABLE, loaded.resultType());
        assertEquals(0L, loaded.claimAmount().oere());
        assertEquals(VatReturnStatus.DRAFT, loaded.status());
    }

    @Test
    void save_claimableReturn_setsClaimAmount() {
        VatReturn claimable = VatReturn.of(
                UUID.randomUUID(), JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(50_000L),   // output VAT
                MonetaryAmount.ofOere(100_000L),  // input VAT > output → CLAIMABLE
                VatReturnStatus.DRAFT,
                Map.of()
        );
        repository.save(claimable);

        VatReturn loaded = repository.findById(claimable.id()).orElseThrow();
        assertEquals(ResultType.CLAIMABLE, loaded.resultType());
        assertEquals(50_000L, loaded.claimAmount().oere());
    }

    @Test
    void findByPeriod_returnsCorrectReturn() {
        VatReturn vatReturn = draftReturn(UUID.randomUUID());
        repository.save(vatReturn);

        Optional<VatReturn> found = repository.findByPeriod(periodId, JurisdictionCode.DK);
        assertTrue(found.isPresent());
        assertEquals(vatReturn.id(), found.get().id());
    }

    @Test
    void updateStatus_draftToSubmitted_setsSubmittedAt() {
        VatReturn vatReturn = draftReturn(UUID.randomUUID());
        repository.save(vatReturn);

        Instant submittedAt = Instant.now();
        VatReturn updated = repository.updateStatus(vatReturn.id(), VatReturnStatus.SUBMITTED, submittedAt);

        assertEquals(VatReturnStatus.SUBMITTED, updated.status());
    }

    @Test
    void updateStatus_submittedToAccepted() {
        VatReturn vatReturn = draftReturn(UUID.randomUUID());
        repository.save(vatReturn);
        repository.updateStatus(vatReturn.id(), VatReturnStatus.SUBMITTED, Instant.now());

        VatReturn accepted = repository.updateStatus(vatReturn.id(), VatReturnStatus.ACCEPTED, Instant.now());
        assertEquals(VatReturnStatus.ACCEPTED, accepted.status());
    }

    @Test
    void findByStatus_returnsOnlyMatchingReturns() {
        VatReturn draft1 = draftReturn(UUID.randomUUID());

        // Need a second period for the second return (unique constraint on period+jurisdiction)
        UUID period2Id = UUID.randomUUID();
        TaxPeriod period2 = new TaxPeriod(period2Id, JurisdictionCode.DK,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        new JooqTaxPeriodRepository(ctx).save(period2, LocalDate.of(2026, 9, 1));

        VatReturn draft2 = VatReturn.of(UUID.randomUUID(), JurisdictionCode.DK, period2Id,
                MonetaryAmount.ofOere(100_000L), MonetaryAmount.ofOere(40_000L),
                VatReturnStatus.DRAFT, Map.of());

        repository.save(draft1);
        repository.save(draft2);
        repository.updateStatus(draft1.id(), VatReturnStatus.SUBMITTED, Instant.now());

        List<VatReturn> drafts = repository.findByStatus(VatReturnStatus.DRAFT);
        assertEquals(1, drafts.size());
        assertEquals(draft2.id(), drafts.get(0).id());

        List<VatReturn> submitted = repository.findByStatus(VatReturnStatus.SUBMITTED);
        assertEquals(1, submitted.size());
        assertEquals(draft1.id(), submitted.get(0).id());
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
