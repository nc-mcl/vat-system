# ADR-002: Technology Stack

## Status
Accepted

## Context
The VAT system requires a technology stack that can:
1. Support a jurisdiction-agnostic plugin architecture from day one (Phase 1: DK, Phase 3: multi-jurisdiction)
2. Handle financial data with absolute correctness (no floating-point errors)
3. Satisfy strict Danish regulatory requirements (Bogføringsloven immutability, SKAT API integration)
4. Scale to ViDA Digital Reporting Requirements (Phase 2: near-real-time transaction reporting by 2028)
5. Be maintainable by multiple agents working concurrently on different packages

The multi-jurisdiction requirement is the dominant architectural driver. Every technology choice must either actively support it or at minimum not hinder it.

## Decision

### Runtime: Node.js 22 LTS
- Single language (TypeScript) across all packages reduces context-switching for agents and developers
- Excellent async I/O for authority API calls (SKAT, VIES) without blocking
- Mature ecosystem for tax/financial tooling
- LTS stability guarantee matters for a compliance system
- Alternative considered: Bun — faster but less mature LTS story and ecosystem compatibility at scale

### API Layer: Hono
- Runs on Node.js, Deno, Bun, and edge runtimes without modification — future-proofs Phase 3 multi-jurisdiction deployments where different regions may require different runtimes
- Zero-dependency core with TypeScript-first design
- Built-in Zod integration via `@hono/zod-validator` matches our validation strategy
- Comparable performance to Fastify with a simpler API surface
- Alternative considered: Fastify — excellent performance but heavier plugin ecosystem to manage; more opinionated on serialization

### Database: PostgreSQL 16 with append-only event tables
- The immutable event ledger (ADR-001) maps naturally to append-only tables with `INSERT` but no `UPDATE`/`DELETE`
- Row-level security (RLS) can enforce immutability at the database layer — `DELETE` and `UPDATE` can be revoked from the application role
- JSONB columns for jurisdiction-specific rubrik fields enable schema-flexible plugin extensions without migrations per jurisdiction
- Strong consistency guarantees for financial data
- `pg_audit` extension for additional audit logging
- Alternative considered: EventStoreDB — purpose-built for event sourcing but adds operational complexity and a separate query model

### Validation: Zod
- Shared schemas can be defined in `/packages/core-domain` and extended by jurisdiction plugins
- Jurisdiction plugins define their own Zod schemas for authority-specific fields (e.g. Danish rubrik validation)
- Runtime type safety bridges the gap between TypeScript compile-time types and external API payloads (SKAT, VIES responses)
- Tree-shakeable — jurisdiction plugins only bundle the schemas they need
- Alternative considered: Yup — similar capability but worse TypeScript inference

### Testing: Vitest
- Native TypeScript/ESM support with no transpilation overhead
- Co-located test files match the package-per-jurisdiction plugin structure
- Fast watch mode critical for jurisdiction plugin development (rapid rule iteration)
- Compatible with Node.js 22 without configuration
- Alternative considered: Jest — requires more configuration for ESM/TypeScript; slower

### Monorepo: npm workspaces
- Native to Node.js — no additional tooling required (no Nx, no Turborepo)
- Each jurisdiction plugin (`/packages/core-domain/src/jurisdictions/dk`) is a logical module within the `core-domain` package; future jurisdictions add new folders, not new workspace packages
- Shared `devDependencies` at root reduce duplication
- `package.json` `exports` map ensures clean package boundaries between agents
- Alternative considered: pnpm workspaces — faster installs but adds a dependency; npm workspaces sufficient for this scale

### Monetary Arithmetic: `bigint` (smallest currency unit)
- All monetary values stored and computed as `bigint` representing øre (DKK), eurocents, or the jurisdiction's smallest unit
- Eliminates IEEE 754 floating-point errors — a compliance requirement for tax systems
- `bigint` operations are exact for the arithmetic operations used in VAT (addition, subtraction, percentage via basis points)
- Enforced by TypeScript type: `type Money = bigint`
- VAT rates expressed as basis points: `2500n` = 25.00%

### Monorepo Structure
```
/packages
  /core-domain          ← Types, JurisdictionPlugin interface, all jurisdiction plugins
  /tax-engine           ← Rate calculation, classification (pure functions, no infra)
  /invoice-validator    ← PEPPOL BIS 3.0 validation
  /skat-client          ← SKAT + VIES API clients (DK-specific, behind AuthorityApiClient interface)
  /reporting            ← VAT return generation, SAF-T, ViDA DRR
  /audit-trail          ← Immutable event log
/apps
  /api                  ← Hono API server
/infrastructure
  /db                   ← PostgreSQL migrations (Drizzle ORM)
```

## Consequences

**Positive:**
- Single language end-to-end eliminates translation errors between layers
- `bigint` enforcement is compile-time-checked — tax calculation errors are caught before runtime
- Zod schemas in `core-domain` serve as the single source of truth for data shapes — jurisdiction plugins extend, not replace
- PostgreSQL JSONB for jurisdiction fields means adding Norway or Germany requires zero schema migrations to core tables
- Hono's runtime portability means authority-specific microservices could be deployed to edge nodes close to each authority's API

**Negative:**
- PostgreSQL JSONB for jurisdiction fields sacrifices some query-time type safety — mitigated by Zod validation on read/write paths
- `bigint` requires explicit serialization to JSON (JSON does not natively support `bigint`) — must use a custom serializer (e.g. `BigInt.toString()` in replacers)
- npm workspaces lack some advanced features (build caching, task orchestration) — acceptable at current scale; revisit in Phase 3

## Alternatives Considered

| Option | Reason Rejected |
|---|---|
| Python + FastAPI | Cross-language boundary between agents adds complexity; TypeScript gives compile-time safety for financial types |
| Java + Spring Boot | Higher operational overhead; slower iteration for a multi-agent setup |
| Deno | Excellent TypeScript-native but ecosystem maturity for PostgreSQL drivers and PEPPOL libraries lags Node.js |
| MongoDB | Document model tempting for JSONB-heavy jurisdiction fields, but lacks ACID transactions needed for financial immutability |
