package com.netcompany.vat.persistence.repository.jooq;

import com.netcompany.vat.domain.Correction;
import com.netcompany.vat.persistence.mapper.CorrectionMapper;
import com.netcompany.vat.persistence.repository.CorrectionRepository;
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
public class JooqCorrectionRepository implements CorrectionRepository {

    private final DSLContext ctx;

    public JooqCorrectionRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Correction save(Correction correction) {
        ctx.insertInto(table("corrections"))
                .columns(
                        field("id"),
                        field("original_return_id"),
                        field("corrected_return_id"),
                        field("reason"),
                        field("corrected_at")
                )
                .values(
                        correction.id(),
                        correction.originalReturnId(),
                        correction.correctedReturnId(),
                        correction.reason(),
                        OffsetDateTime.now(ZoneOffset.UTC)
                )
                .execute();
        return correction;
    }

    @Override
    public Optional<Correction> findById(UUID id) {
        return ctx.selectFrom(table("corrections"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional(CorrectionMapper::fromRecord);
    }

    @Override
    public List<Correction> findByOriginalReturn(UUID originalReturnId) {
        return ctx.selectFrom(table("corrections"))
                .where(field("original_return_id", UUID.class).eq(originalReturnId))
                .fetch(CorrectionMapper::fromRecord);
    }
}
