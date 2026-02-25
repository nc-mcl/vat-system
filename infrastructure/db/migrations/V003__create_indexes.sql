-- V003__create_indexes.sql
-- Additional composite indexes for API query patterns.
-- Core single-column indexes are co-located with their table DDL in V001/V002.

-- Most common repository query: fetch all transactions for a period
CREATE INDEX IF NOT EXISTS idx_transactions_period
    ON transactions (period_id);

-- Efficiently find pending/draft VAT returns for processing pipelines
CREATE INDEX IF NOT EXISTS idx_vat_returns_status
    ON vat_returns (status);

-- Entity history lookup in the audit log
CREATE INDEX IF NOT EXISTS idx_audit_aggregate_id
    ON audit_log (aggregate_id);

-- Lookup open tax periods for a jurisdiction on a given date
CREATE INDEX IF NOT EXISTS idx_tax_periods_jurisdiction_status
    ON tax_periods (jurisdiction_code, status);

-- Lookup VAT returns for a specific period quickly
CREATE INDEX IF NOT EXISTS idx_vat_returns_period
    ON vat_returns (period_id);
