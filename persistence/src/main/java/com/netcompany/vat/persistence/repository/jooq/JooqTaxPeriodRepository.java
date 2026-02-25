package com.netcompany.vat.persistence.repository.jooq;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.persistence.mapper.TaxPeriodMapper;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

@Repository
public class JooqTaxPeriodRepository implements TaxPeriodRepository {

    private final DSLContext ctx;

    public JooqTaxPeriodRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<TaxPeriod> findById(UUID id) {
        return ctx.selectFrom(table("tax_periods"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional(TaxPeriodMapper::fromRecord);
    }

    @Override
    public List<TaxPeriod> findByJurisdiction(JurisdictionCode code) {
        return ctx.selectFrom(table("tax_periods"))
                .where(field("jurisdiction_code", String.class).eq(code.name()))
                .fetch(TaxPeriodMapper::fromRecord);
    }

    @Override
    public Optional<TaxPeriod> findOpenPeriod(JurisdictionCode code, LocalDate date) {
        return ctx.selectFrom(table("tax_periods"))
                .where(field("jurisdiction_code", String.class).eq(code.name()))
                .and(field("status", String.class).eq(TaxPeriodStatus.OPEN.name()))
                .and(field("period_start", LocalDate.class).le(date))
                .and(field("period_end", LocalDate.class).ge(date))
                .fetchOptional(TaxPeriodMapper::fromRecord);
    }

    @Override
    public TaxPeriod save(TaxPeriod period, LocalDate filingDeadline) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ctx.insertInto(table("tax_periods"))
                .columns(
                        field("id"),
                        field("jurisdiction_code"),
                        field("period_start"),
                        field("period_end"),
                        field("filing_cadence"),
                        field("filing_deadline"),
                        field("status"),
                        field("created_at"),
                        field("updated_at")
                )
                .values(
                        period.id(),
                        period.jurisdictionCode().name(),
                        period.startDate(),
                        period.endDate(),
                        period.cadence().name(),
                        filingDeadline,
                        period.status().name(),
                        now,
                        now
                )
                .execute();
        return period;
    }

    @Override
    public TaxPeriod updateStatus(UUID id, TaxPeriodStatus status) {
        ctx.update(table("tax_periods"))
                .set(field("status"), status.name())
                .set(field("updated_at"), OffsetDateTime.now(ZoneOffset.UTC))
                .where(field("id", UUID.class).eq(id))
                .execute();
        return findById(id)
                .orElseThrow(() -> new IllegalStateException("TaxPeriod not found: " + id));
    }
}
