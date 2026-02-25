package com.netcompany.vat.persistence.repository;

import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.JurisdictionCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for {@link Counterparty}. */
public interface CounterpartyRepository {

    Counterparty save(Counterparty counterparty);

    Optional<Counterparty> findById(UUID id);

    Optional<Counterparty> findByVatNumber(String vatNumber, JurisdictionCode code);

    List<Counterparty> findByJurisdiction(JurisdictionCode code);
}
