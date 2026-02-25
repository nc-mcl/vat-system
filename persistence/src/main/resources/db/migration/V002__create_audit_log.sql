-- V002__create_audit_log.sql
-- Immutable append-only audit event log.
--
-- IMPORTANT — Bogføringsloven compliance:
--   This table must NEVER be updated or deleted from by any application user.
--   Danish bookkeeping law (Bogføringsloven) requires records to be retained for
--   5 years and prohibits retroactive modification.  Row-level security or a
--   dedicated write-only role should be used in production to enforce this at the
--   database level.
--
-- The BIGSERIAL primary key guarantees strict insertion-order across concurrent
-- writers without gaps that would arise from UUIDs.

CREATE TABLE audit_log (
    id               BIGSERIAL   NOT NULL,
    event_time       TIMESTAMPTZ NOT NULL DEFAULT now(),
    aggregate_type   VARCHAR(50) NOT NULL,  -- e.g. 'VatReturn', 'Transaction', 'TaxPeriod'
    aggregate_id     UUID        NOT NULL,
    event_type       VARCHAR(100) NOT NULL, -- e.g. 'RETURN_ASSEMBLED', 'TRANSACTION_CREATED'
    actor            VARCHAR(255),          -- user or system identity that triggered the event
    payload          JSONB        NOT NULL, -- full entity snapshot at event time
    jurisdiction_code VARCHAR(10) NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

-- Ordered history for a specific entity
CREATE INDEX idx_audit_aggregate ON audit_log (aggregate_type, aggregate_id);
-- Time-range queries across all events
CREATE INDEX idx_audit_event_time ON audit_log (event_time);

COMMENT ON TABLE audit_log IS
    'Immutable event log.  No UPDATE or DELETE is permitted on any row.  '
    'Bogføringsloven requires retention for 5 years.';
