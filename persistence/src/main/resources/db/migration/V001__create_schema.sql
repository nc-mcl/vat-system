-- V001__create_schema.sql
-- Core VAT tables.  All monetary values are stored as BIGINT in øre (smallest DKK unit).
-- Enums are stored as VARCHAR using the Java enum name() — never ordinal.
-- Records are immutable: no rows are ever deleted from transactional tables.

-- ---------------------------------------------------------------------------
-- counterparties: legal entities involved in VAT transactions
-- ---------------------------------------------------------------------------
CREATE TABLE counterparties (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    jurisdiction_code VARCHAR(10) NOT NULL,
    vat_number        VARCHAR(50),
    name              VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_counterparties PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- tax_periods: time windows for which a taxpayer must file a VAT return
-- ---------------------------------------------------------------------------
CREATE TABLE tax_periods (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    jurisdiction_code VARCHAR(10) NOT NULL,
    period_start      DATE        NOT NULL,
    period_end        DATE        NOT NULL,
    filing_cadence    VARCHAR(20) NOT NULL,
    filing_deadline   DATE        NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_tax_periods            PRIMARY KEY (id),
    CONSTRAINT chk_period_end_after_start CHECK (period_end > period_start),
    CONSTRAINT chk_filing_cadence CHECK (
        filing_cadence IN ('MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL')
    ),
    CONSTRAINT chk_tax_period_status CHECK (
        status IN ('OPEN','FILED','ASSESSED','CORRECTED')
    )
);

-- ---------------------------------------------------------------------------
-- transactions: economic events that affect VAT position within a period
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    jurisdiction_code    VARCHAR(10) NOT NULL,
    period_id            UUID        NOT NULL,
    counterparty_id      UUID,
    transaction_date     DATE        NOT NULL,
    description          VARCHAR(500),
    amount_excl_vat      BIGINT      NOT NULL,
    vat_amount           BIGINT      NOT NULL,
    tax_code             VARCHAR(30) NOT NULL,
    rate_basis_points    INT         NOT NULL,
    is_reverse_charge    BOOLEAN     NOT NULL DEFAULT false,
    classification_date  DATE        NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_transactions               PRIMARY KEY (id),
    CONSTRAINT fk_transaction_period         FOREIGN KEY (period_id)
        REFERENCES tax_periods(id),
    CONSTRAINT fk_transaction_counterparty   FOREIGN KEY (counterparty_id)
        REFERENCES counterparties(id),
    CONSTRAINT chk_tax_code CHECK (
        tax_code IN ('STANDARD','ZERO_RATED','EXEMPT','REVERSE_CHARGE','OUT_OF_SCOPE')
    )
);

CREATE INDEX idx_transactions_period_taxcode ON transactions (period_id, tax_code);
CREATE INDEX idx_transactions_period_date    ON transactions (period_id, transaction_date);

-- ---------------------------------------------------------------------------
-- vat_returns: top-level aggregate for a single VAT filing period
-- ---------------------------------------------------------------------------
CREATE TABLE vat_returns (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    period_id           UUID        NOT NULL,
    jurisdiction_code   VARCHAR(10) NOT NULL,
    output_vat          BIGINT      NOT NULL,
    input_vat           BIGINT      NOT NULL,
    net_vat             BIGINT      NOT NULL,
    result_type         VARCHAR(20) NOT NULL,
    claim_amount        BIGINT      NOT NULL DEFAULT 0,
    status              VARCHAR(30) NOT NULL,
    jurisdiction_fields JSONB,
    -- Convenience columns for DK rubrik queries (also present in jurisdiction_fields JSONB)
    rubrik_a            BIGINT      NOT NULL DEFAULT 0,
    rubrik_b            BIGINT      NOT NULL DEFAULT 0,
    assembled_at        TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    accepted_at         TIMESTAMPTZ,
    skat_reference      VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_vat_returns                      PRIMARY KEY (id),
    CONSTRAINT fk_vat_return_period                FOREIGN KEY (period_id)
        REFERENCES tax_periods(id),
    CONSTRAINT uq_vat_return_period_jurisdiction   UNIQUE (period_id, jurisdiction_code),
    CONSTRAINT chk_result_type CHECK (
        result_type IN ('PAYABLE','CLAIMABLE','ZERO')
    ),
    CONSTRAINT chk_vat_return_status CHECK (
        status IN ('DRAFT','SUBMITTED','ACCEPTED','REJECTED')
    )
);

-- ---------------------------------------------------------------------------
-- corrections: links original VAT return to its corrected replacement
-- ---------------------------------------------------------------------------
CREATE TABLE corrections (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    original_return_id  UUID        NOT NULL,
    corrected_return_id UUID        NOT NULL,
    reason              TEXT        NOT NULL,
    corrected_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    corrected_by        VARCHAR(255),
    CONSTRAINT pk_corrections               PRIMARY KEY (id),
    CONSTRAINT fk_correction_original       FOREIGN KEY (original_return_id)
        REFERENCES vat_returns(id),
    CONSTRAINT fk_correction_corrected      FOREIGN KEY (corrected_return_id)
        REFERENCES vat_returns(id)
);
