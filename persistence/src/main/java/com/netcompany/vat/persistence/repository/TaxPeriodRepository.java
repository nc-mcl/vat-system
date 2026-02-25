package com.netcompany.vat.persistence.repository;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TaxPeriod} aggregate.
 *
 * <p>Note: {@code save} accepts a {@code filingDeadline} parameter because the domain record
 * does not store it — the deadline is computed by the {@code JurisdictionPlugin} at the
 * service layer and passed in for persistence.
 */
public interface TaxPeriodRepository {

    Optional<TaxPeriod> findById(UUID id);

    List<TaxPeriod> findByJurisdiction(JurisdictionCode code);

    /** Returns the OPEN period for a jurisdiction that contains the given date, if any. */
    Optional<TaxPeriod> findOpenPeriod(JurisdictionCode code, LocalDate date);

    /** Persists a new tax period together with its pre-computed filing deadline. */
    TaxPeriod save(TaxPeriod period, LocalDate filingDeadline);

    TaxPeriod updateStatus(UUID id, TaxPeriodStatus status);
}
