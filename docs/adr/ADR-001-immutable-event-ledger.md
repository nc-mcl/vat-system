# ADR-001: Use Immutable Event Ledger for VAT Transactions

## Status
Proposed

## Context
Danish Bogføringsloven and EU VAT rules prohibit deletion of accounting records.
All corrections must be traceable. ViDA DRR requires real-time reporting of transactions.

## Decision
All VAT transactions are stored as immutable append-only events.
Corrections are new events referencing the original, never updates to existing records.

## Consequences
- Full audit trail out of the box
- Simplifies ViDA real-time reporting (events can be streamed directly)
- Slightly more complex query logic (must reconstruct current state from event chain)
- Naturally supports SAF-T export format
