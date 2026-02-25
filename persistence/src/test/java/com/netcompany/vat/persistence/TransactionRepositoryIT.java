package com.netcompany.vat.persistence;

import com.netcompany.vat.domain.*;
import com.netcompany.vat.persistence.repository.jooq.JooqTaxPeriodRepository;
import com.netcompany.vat.persistence.repository.jooq.JooqTransactionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRepositoryIT extends AbstractRepositoryIT {

    private JooqTransactionRepository repository;
    private UUID periodId;

    @BeforeAll
    static void setUp() {
        initDatabase();
    }

    @BeforeEach
    void reset() {
        truncateTables();
        repository = new JooqTransactionRepository(ctx);

        // Every transaction needs a parent period
        periodId = UUID.randomUUID();
        TaxPeriod period = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        new JooqTaxPeriodRepository(ctx).save(period, LocalDate.of(2026, 6, 1));
    }

    private Transaction standardTransaction(UUID id) {
        TaxClassification classification = new TaxClassification(
                TaxCode.STANDARD, 2500L, false, LocalDate.of(2026, 1, 1));
        return new Transaction(
                id, JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 1), "Test sale",
                MonetaryAmount.ofOere(100_000L), classification, Instant.now());
    }

    @Test
    void save_and_findByPeriod_roundTrip() {
        Transaction tx = standardTransaction(UUID.randomUUID());
        repository.save(tx);

        List<Transaction> found = repository.findByPeriod(periodId);
        assertEquals(1, found.size());
        Transaction loaded = found.get(0);

        assertEquals(tx.id(), loaded.id());
        assertEquals(JurisdictionCode.DK, loaded.jurisdictionCode());
        assertEquals(100_000L, loaded.amountExclVat().oere());
        assertEquals(TaxCode.STANDARD, loaded.classification().taxCode());
        assertEquals(2500L, loaded.classification().rateInBasisPoints());
        assertFalse(loaded.classification().isReverseCharge());
    }

    @Test
    void findByPeriodAndTaxCode_filtersCorrectly() {
        TaxClassification standardClass = new TaxClassification(
                TaxCode.STANDARD, 2500L, false, LocalDate.of(2026, 1, 1));
        TaxClassification zeroClass = new TaxClassification(
                TaxCode.ZERO_RATED, 0L, false, LocalDate.of(2026, 1, 1));

        Transaction t1 = new Transaction(UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 1), "Standard sale",
                MonetaryAmount.ofOere(50_000L), standardClass, Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 2), "Export",
                MonetaryAmount.ofOere(30_000L), zeroClass, Instant.now());

        repository.save(t1);
        repository.save(t2);

        List<Transaction> standardOnly = repository.findByPeriodAndTaxCode(periodId, TaxCode.STANDARD);
        assertEquals(1, standardOnly.size());
        assertEquals(TaxCode.STANDARD, standardOnly.get(0).classification().taxCode());
    }

    @Test
    void sumVatByPeriod_sumsMontaryAmountsInOere() {
        // t1: 100,000 øre * 25% = 25,000 øre VAT
        // t2: 200,000 øre * 25% = 50,000 øre VAT
        // Total = 75,000 øre
        TaxClassification classification = new TaxClassification(
                TaxCode.STANDARD, 2500L, false, LocalDate.of(2026, 1, 1));

        Transaction t1 = new Transaction(UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 1), null,
                MonetaryAmount.ofOere(100_000L), classification, Instant.now());
        Transaction t2 = new Transaction(UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 2), null,
                MonetaryAmount.ofOere(200_000L), classification, Instant.now());

        repository.save(t1);
        repository.save(t2);

        long total = repository.sumVatByPeriod(periodId);
        assertEquals(75_000L, total);
    }

    @Test
    void sumVatByPeriod_returnsZero_whenNoPeriodTransactions() {
        assertEquals(0L, repository.sumVatByPeriod(UUID.randomUUID()));
    }

    @Test
    void save_withReverseCharge_persistsFlag() {
        TaxClassification rcClass = new TaxClassification(
                TaxCode.REVERSE_CHARGE, 2500L, true, LocalDate.of(2026, 1, 1));
        Transaction tx = new Transaction(UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 2, 1), "Cross-border B2B service",
                MonetaryAmount.ofOere(80_000L), rcClass, Instant.now());

        repository.save(tx);

        Transaction loaded = repository.findByPeriod(periodId).get(0);
        assertTrue(loaded.classification().isReverseCharge());
        assertEquals(TaxCode.REVERSE_CHARGE, loaded.classification().taxCode());
    }
}
