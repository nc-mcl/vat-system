package com.netcompany.vat.persistence.repository.jooq;

import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.persistence.mapper.TransactionMapper;
import com.netcompany.vat.persistence.repository.TransactionRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqTransactionRepository implements TransactionRepository {

    private final DSLContext ctx;

    public JooqTransactionRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Transaction save(Transaction transaction) {
        OffsetDateTime createdAt = transaction.createdAt() != null
                ? transaction.createdAt().atOffset(ZoneOffset.UTC)
                : OffsetDateTime.now(ZoneOffset.UTC);

        ctx.insertInto(table("transactions"))
                .columns(
                        field("id"),
                        field("jurisdiction_code"),
                        field("period_id"),
                        field("counterparty_id"),
                        field("transaction_date"),
                        field("description"),
                        field("amount_excl_vat"),
                        field("vat_amount"),
                        field("tax_code"),
                        field("rate_basis_points"),
                        field("is_reverse_charge"),
                        field("classification_date"),
                        field("created_at")
                )
                .values(
                        transaction.id(),
                        transaction.jurisdictionCode().name(),
                        transaction.periodId(),
                        transaction.counterpartyId(),
                        transaction.transactionDate(),
                        transaction.description(),
                        transaction.amountExclVat().oere(),
                        transaction.vatAmount().oere(),
                        transaction.classification().taxCode().name(),
                        (int) transaction.classification().rateInBasisPoints(),
                        transaction.classification().isReverseCharge(),
                        transaction.classification().effectiveDate(),
                        createdAt
                )
                .execute();
        return transaction;
    }

    @Override
    public List<Transaction> findByPeriod(UUID periodId) {
        return ctx.selectFrom(table("transactions"))
                .where(field("period_id", UUID.class).eq(periodId))
                .fetch(TransactionMapper::fromRecord);
    }

    @Override
    public List<Transaction> findByPeriodAndTaxCode(UUID periodId, TaxCode taxCode) {
        return ctx.selectFrom(table("transactions"))
                .where(field("period_id", UUID.class).eq(periodId))
                .and(field("tax_code", String.class).eq(taxCode.name()))
                .fetch(TransactionMapper::fromRecord);
    }

    @Override
    public long sumVatByPeriod(UUID periodId) {
        Long result = ctx.select(
                        org.jooq.impl.DSL.sum(field("vat_amount", Long.class)))
                .from(table("transactions"))
                .where(field("period_id", UUID.class).eq(periodId))
                .fetchOne(0, Long.class);
        return result != null ? result : 0L;
    }
}
