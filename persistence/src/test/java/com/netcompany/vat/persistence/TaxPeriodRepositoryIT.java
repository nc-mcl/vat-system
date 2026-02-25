package com.netcompany.vat.persistence;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.persistence.repository.jooq.JooqTaxPeriodRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaxPeriodRepositoryIT extends AbstractRepositoryIT {

    private JooqTaxPeriodRepository repository;

    @BeforeAll
    static void setUp() {
        initDatabase();
    }

    @BeforeEach
    void reset() {
        truncateTables();
        repository = new JooqTaxPeriodRepository(ctx);
    }

    @Test
    void save_and_findById_roundTrip() {
        TaxPeriod period = new TaxPeriod(
                UUID.randomUUID(),
                JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY,
                TaxPeriodStatus.OPEN
        );
        LocalDate deadline = LocalDate.of(2026, 6, 1);

        repository.save(period, deadline);

        Optional<TaxPeriod> found = repository.findById(period.id());
        assertTrue(found.isPresent());
        TaxPeriod loaded = found.get();

        assertEquals(period.id(), loaded.id());
        assertEquals(JurisdictionCode.DK, loaded.jurisdictionCode());
        assertEquals(LocalDate.of(2026, 1, 1), loaded.startDate());
        assertEquals(LocalDate.of(2026, 3, 31), loaded.endDate());
        assertEquals(FilingCadence.QUARTERLY, loaded.cadence());
        assertEquals(TaxPeriodStatus.OPEN, loaded.status());
    }

    @Test
    void findByJurisdiction_returnsOnlyDkPeriods() {
        TaxPeriod p1 = new TaxPeriod(UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        TaxPeriod p2 = new TaxPeriod(UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);

        repository.save(p1, LocalDate.of(2026, 6, 1));
        repository.save(p2, LocalDate.of(2026, 9, 1));

        List<TaxPeriod> results = repository.findByJurisdiction(JurisdictionCode.DK);
        assertEquals(2, results.size());
    }

    @Test
    void findOpenPeriod_returnsCorrectPeriodForDate() {
        TaxPeriod period = new TaxPeriod(UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        repository.save(period, LocalDate.of(2026, 6, 1));

        Optional<TaxPeriod> found = repository.findOpenPeriod(JurisdictionCode.DK,
                LocalDate.of(2026, 2, 15));
        assertTrue(found.isPresent());
        assertEquals(period.id(), found.get().id());
    }

    @Test
    void findOpenPeriod_returnsEmpty_whenDateOutsidePeriod() {
        TaxPeriod period = new TaxPeriod(UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        repository.save(period, LocalDate.of(2026, 6, 1));

        Optional<TaxPeriod> found = repository.findOpenPeriod(JurisdictionCode.DK,
                LocalDate.of(2026, 7, 1));
        assertTrue(found.isEmpty());
    }

    @Test
    void updateStatus_changesStatusAndReturnsUpdatedPeriod() {
        TaxPeriod period = new TaxPeriod(UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        repository.save(period, LocalDate.of(2026, 6, 1));

        TaxPeriod updated = repository.updateStatus(period.id(), TaxPeriodStatus.FILED);

        assertEquals(TaxPeriodStatus.FILED, updated.status());
        assertEquals(period.id(), updated.id());
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
