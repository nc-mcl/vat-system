package com.netcompany.vat.persistence.repository;

import com.netcompany.vat.domain.Correction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Correction}.
 * Corrections are append-only: the original return is never modified.
 */
public interface CorrectionRepository {

    Correction save(Correction correction);

    Optional<Correction> findById(UUID id);

    List<Correction> findByOriginalReturn(UUID originalReturnId);
}
