package com.netcompany.vat.persistence.repository;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link VatReturn} aggregate.
 *
 * <p>VAT returns are immutable once accepted.  Corrections create a new return
 * and link it via a {@link CorrectionRepository} entry — the original is never modified.
 */
public interface VatReturnRepository {

    VatReturn save(VatReturn vatReturn);

    Optional<VatReturn> findById(UUID id);

    Optional<VatReturn> findByPeriod(UUID periodId, JurisdictionCode code);

    List<VatReturn> findByStatus(VatReturnStatus status);

    /**
     * Transitions the return to a new status and records the event timestamp.
     * The {@code AuditLogger} must be called before invoking this method.
     */
    VatReturn updateStatus(UUID id, VatReturnStatus status, Instant timestamp);
}
