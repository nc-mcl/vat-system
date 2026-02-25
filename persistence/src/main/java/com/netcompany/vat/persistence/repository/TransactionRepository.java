package com.netcompany.vat.persistence.repository;

import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Transaction}.  Transactions are immutable once persisted.
 */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    List<Transaction> findByPeriod(UUID periodId);

    List<Transaction> findByPeriodAndTaxCode(UUID periodId, TaxCode taxCode);

    /** Returns the total VAT amount for all transactions in the period, in øre. */
    long sumVatByPeriod(UUID periodId);
}
