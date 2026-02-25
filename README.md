# VAT System

A multi-jurisdiction VAT processing system built with ViDA (VAT in the Digital Age) compliance in mind.
Danish VAT (MOMS) is the first jurisdiction; the architecture is designed so additional countries can be added as plugins with zero changes to the core.

---

## What This System Does

The VAT system handles the full lifecycle of VAT compliance for businesses operating in Denmark:

1. **Classify transactions** — determine the correct VAT treatment (standard rate, zero-rated, exempt, reverse charge)
2. **Calculate VAT** — compute output VAT, input VAT, and net VAT using exact integer arithmetic (no floating point)
3. **Assemble VAT returns** — aggregate transactions into a period return with SKAT rubrik fields
4. **File with SKAT** — submit the return electronically to the Danish tax authority
5. **Receive assessment** — record the SKAT response (accepted, queried, or rejected)
6. **Maintain an immutable audit trail** — every state change is logged and retained for 5 years (Bogføringsloven)

---

## Project Status — Phase 1 In Progress

<!-- BUILD_STATUS_TABLE: Agents must update this table as part of every handoff. -->
<!-- Status values: ✅ Done | 🔄 In Progress | ⏳ Pending -->
<!-- Do not remove or rename the BUILD_STATUS_TABLE comment — it is used to locate this table. -->

| Layer | Status | Description |
|---|---|---|
| Domain Knowledge | ✅ Done | 32 verified Danish VAT rules, risk register, gap analysis |
| Architecture | ✅ Done | Java 21, Spring Boot, Gradle multi-module, jurisdiction plugin pattern |
| Core Domain | ✅ Done | All Java types, DK plugin, `MonetaryAmount`, `Result<T>` |
| Tax Engine | ✅ Done | 68 tests passing — VAT calculation, classification, filing periods |
| Design Diagrams | ✅ Done | 8 Mermaid diagrams covering the full system |
| Dev Container | ✅ Done | VS Code dev container, Docker Compose, CI/CD pipeline |
| Persistence | ✅ Done | PostgreSQL schema, Flyway migrations, JOOQ repositories, audit ledger |
| REST API | ✅ Done | Spring Boot REST endpoints, Bean Validation, DTOs, 17 tests passing |
| SKAT Integration | ⏳ Pending | SKAT API client, PEPPOL e-invoicing, VIES VAT validation |
| End-to-End Tests | ⏳ Pending | Full filing scenarios against SKAT sandbox |

<!-- Last updated by: API Agent — 2026-02-25 (Persistence ✅ Done; REST API ✅ Done) -->

### Phase 1 Roadmap

```
What exists now (tax engine + DevOps scaffolding)
      │
      ▼
┌─────────────────┐
│  Persistence    │  Store transactions and VAT returns in PostgreSQL
│  Agent          │  Flyway migrations, JOOQ repositories, audit trail
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  API Agent      │  Expose the tax engine via REST endpoints
│                 │  POST /transactions, POST /returns, GET /periods
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Integration    │  Connect to SKAT for real filing
│  Agent          │  SKAT API client, PEPPOL e-invoicing
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Testing Agent  │  End-to-end tests covering full filing scenarios
│                 │  Real PostgreSQL, real SKAT sandbox
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Phase 1 Done   │  Danish VAT filing and assessment working end-to-end
└─────────────────┘
```

### Phase 1 "Done" Definition

Phase 1 is complete when this full flow works via the REST API:

```bash
# Step 1 — Register a business
POST /api/v1/businesses
{ "cvrNumber": "12345678", "name": "Acme ApS" }

# Step 2 — Submit transactions
POST /api/v1/transactions
{ "periodId": "...", "amountExclVat": 100000, "taxCode": "STANDARD" }

# Step 3 — Assemble a VAT return
POST /api/v1/returns/assemble
{ "periodId": "...", "jurisdictionCode": "DK" }

# Step 4 — File with SKAT
POST /api/v1/returns/{id}/submit

# Step 5 — Get assessment
GET /api/v1/returns/{id}
→ { "netVat": 25000, "resultType": "PAYABLE", "status": "ACCEPTED" }
```

---

## Architecture

The architecture follows a **jurisdiction plugin pattern** — all core logic is country-neutral. Danish VAT is a plugin. Adding a new country requires only a new plugin with zero changes to core.

- Jurisdiction-agnostic core domain and plugin SPI (`JurisdictionPlugin`)
- Danish rules implemented as a jurisdiction plugin (`DkJurisdictionPlugin`)
- Pure business rules in `tax-engine` (no infrastructure dependencies)
- Persistence concerns in `persistence` (JOOQ + Flyway)
- API and orchestration in `api` (Spring Boot)
- External authority integration in `skat-client` (SKAT, VIES, PEPPOL)
- Separate `mcp-server` (TypeScript/Node.js) — not part of the Java backend runtime

**Key design decisions:**
- **Immutability** — VAT records are never deleted, only corrected via credit notes
- **Audit first** — every state change is logged before it takes effect
- **No floating point** — all monetary values are stored as `long` in øre (1 DKK = 100 øre)
- **Rule isolation** — all VAT business rules live in `/tax-engine` with zero infrastructure dependencies
- **ViDA ready** — architecture supports phased ViDA rollout (Digital Reporting Requirements from 2028)

---

## Module Structure

Gradle submodules (from `settings.gradle.kts`):
- `core-domain` — pure Java domain model, `JurisdictionPlugin` interface, DK plugin
- `tax-engine` — pure Java business logic, no framework dependencies
- `persistence` — JOOQ + Flyway + repositories + immutable audit ledger
- `api` — Spring Boot REST API
- `skat-client` — SKAT API, VIES VAT validation, PEPPOL e-invoicing

Non-Gradle module:
- `mcp-server` — TypeScript MCP tooling (Claude Code context server, not part of Java runtime)

---

## Getting Started

### Prerequisites
- JDK 21
- Node.js 18+ (for MCP server only)
- Docker Desktop (for local dev with Docker Compose or dev container)

### Run locally with Docker Compose

```bash
docker compose up
```

This starts:
- `api` — Spring Boot application on port 8080
- `postgres` — PostgreSQL 16 on port 5432
- `adminer` — database UI on port 8090

### Run in the Dev Container

Open the project in VS Code and select **Reopen in Container** when prompted.
The container includes Java 21, Gradle, and all tooling pre-configured.

## Devcontainer Troubleshooting (Windows/WSL)

- Prereq: Use a modern WSL2 distro as the default (Ubuntu 22.04 recommended). Check with `wsl --list --verbose` and set with `wsl --set-default Ubuntu-22.04`.
- If you see `Dockerfile-with-features` failing with `unknown instruction: FROM (`, ensure `.devcontainer/Dockerfile` is UTF-8 without BOM and uses LF line endings.
- Smoke test the setup from PowerShell: `.\scripts\devcontainer-smoketest.ps1` (add `-Start` to launch the dev container).

### Build and test (Java)

```bash
./gradlew clean build   # compile + test all modules
./gradlew test          # run tests only
./gradlew :api:bootRun  # run the API module only
```

### Run the MCP server (separate process)

```bash
cd mcp-server
npm install
npm run dev
```

---

## Deploying to Kubernetes

Kubernetes manifests live in `infrastructure/k8s/`. All manifests are validated with `kubectl apply --dry-run=client`.

```bash
# Create namespace and secrets
kubectl apply -f infrastructure/k8s/cluster/namespace.yaml
kubectl create secret generic vat-secrets \
  --from-literal=db-password=<password> \
  --from-literal=skat-api-key=<key> \
  -n vat-system

# Deploy PostgreSQL
kubectl apply -f infrastructure/k8s/postgres/

# Deploy API
kubectl apply -f infrastructure/k8s/api/
```

See `infrastructure/README.md` for full deployment instructions.

---

## Environment Variables

| Variable | Required | Default | Used By | Description |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes (runtime) | — | `api`, `persistence` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes (runtime) | — | `api`, `persistence` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Yes (runtime) | — | `api`, `persistence` | PostgreSQL password |
| `SKAT_BASE_URL` | No | `https://api-sandbox.skat.dk` | `api`, `skat-client` | SKAT API base URL |
| `SKAT_API_KEY` | Yes (SKAT calls) | empty | `api`, `skat-client` | SKAT API credential |
| `VIES_BASE_URL` | No | EU VIES endpoint | `api`, `skat-client` | VIES validation endpoint |

---

## Danish VAT — Key Rules

| Rule | Detail |
|---|---|
| Standard rate | 25% (MOMS) |
| Zero-rated | Exports, intra-EU goods supplies |
| Exempt | Healthcare, education, insurance (no VAT recovery) |
| Reverse charge | B2B cross-border services — buyer accounts for VAT |
| Filing cadence | Semi-annual (<5M DKK), quarterly (5–50M DKK), monthly (>50M DKK) |
| Authority | SKAT (Skattestyrelsen) — https://skat.dk |
| E-invoicing | NemHandel / PEPPOL BIS 3.0 |
| Record retention | 5 years — Bogføringsloven (Danish Bookkeeping Act) |

---

## Phase Roadmap

| Phase | Scope | Status |
|---|---|---|
| Phase 1 (MVP) | Danish VAT — rates, filing, SKAT integration, audit trail | 🔄 In progress |
| Phase 2 (ViDA) | Digital Reporting Requirements (DRR), extended OSS, platform economy rules | ⏳ Planned |
| Phase 3 (Multi-jurisdiction) | Additional EU countries as plugins — Norway, Germany, etc. | ⏳ Planned |

---

## Known Gaps (resolve before production)

The Business Analyst Agent identified three gaps that need expert review before real SKAT filings:

- **G2** — Exact filing deadlines for monthly filers (current implementation is a best estimate)
- **G3** — Non-EU service rubrik scope (which rubriks apply to non-EU services is unclear)
- **G5** — Construction reverse charge under Momslovens §46 (unconfirmed)

These do not block continued development but must be resolved before going live.

---

## License Policy

This repository enforces an open-source-only dependency policy (see `ROLE_CONTEXT_POLICY.md`):

- **Allowed:** Apache 2.0, MIT, BSD, EPL, LGPL (with distribution caution)
- **Forbidden:** proprietary/commercial, GPL, AGPL, royalty-triggering licenses

Verify license compatibility before adding any dependency.

---

## Contributing

This project is built using a multi-agent architecture orchestrated via Claude Code.
Each agent has an operating contract in `/agents/<agent-name>/`. See `CLAUDE.md` for the master context
and `ROLE_CONTEXT_POLICY.md` for mandatory constraints all agents must follow.

Development workflow:
- Use Gradle as the source of truth for Java module builds
- Keep domain logic framework-free in `core-domain` and `tax-engine`
- Follow the agent handoff protocol in `ROLE_CONTEXT_POLICY.md` — every agent must update `CLAUDE.md`, `README.md` status table, `docs/agent-sessions/session-log.md`, and component READMEs before finishing
