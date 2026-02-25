package com.netcompany.vat.persistence.repository.jooq;

import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.persistence.mapper.CounterpartyMapper;
import com.netcompany.vat.persistence.repository.CounterpartyRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqCounterpartyRepository implements CounterpartyRepository {

    private final DSLContext ctx;

    public JooqCounterpartyRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Counterparty save(Counterparty counterparty) {
        ctx.insertInto(table("counterparties"))
                .columns(
                        field("id"),
                        field("jurisdiction_code"),
                        field("vat_number"),
                        field("name"),
                        field("created_at")
                )
                .values(
                        counterparty.id(),
                        counterparty.jurisdictionCode().name(),
                        counterparty.vatNumber(),
                        counterparty.name(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
                .execute();
        return counterparty;
    }

    @Override
    public Optional<Counterparty> findById(UUID id) {
        return ctx.selectFrom(table("counterparties"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional(CounterpartyMapper::fromRecord);
    }

    @Override
    public Optional<Counterparty> findByVatNumber(String vatNumber, JurisdictionCode code) {
        return ctx.selectFrom(table("counterparties"))
                .where(field("vat_number", String.class).eq(vatNumber))
                .and(field("jurisdiction_code", String.class).eq(code.name()))
                .fetchOptional(CounterpartyMapper::fromRecord);
    }

    @Override
    public List<Counterparty> findByJurisdiction(JurisdictionCode code) {
        return ctx.selectFrom(table("counterparties"))
                .where(field("jurisdiction_code", String.class).eq(code.name()))
                .fetch(CounterpartyMapper::fromRecord);
    }
}
