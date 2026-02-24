# C4 Container Diagram — VAT System

**What this shows:** All deployable containers (services, databases, libraries) inside the VAT system boundary and how they communicate. The Tax Engine is a Java library (not a separate service) — it runs in-process with the API.

**Last updated:** 2026-02-24
**Produced by:** Design Agent

---

```mermaid
C4Container
  title Container Diagram — VAT System

  Person(business, "Business / Accountant", "Files VAT returns and manages transactions")

  System_Ext(skat, "SKAT API", "Danish Tax Authority — receives submitted returns")
  System_Ext(vies, "VIES", "EU VAT number validation")
  System_Ext(peppol, "PEPPOL / NemHandel", "E-invoicing network")

  System_Boundary(vatSystem, "VAT System") {

    Container(api, "REST API", "Java 21, Spring Boot 3.3", "Handles all client HTTP requests. Orchestrates tax calculation, persistence, and SKAT submission. Exposes /actuator/health for liveness and readiness probes.")

    Container(taxEngine, "Tax Engine", "Java 21 library (no framework)", "Pure business logic: VAT rate calculation, transaction classification, reverse charge determination, period closing rules. Zero infrastructure dependencies. Runs in-process with the API.")

    Container(skatClient, "SKAT Client", "Java 21, Spring WebClient", "Anti-corruption layer for all external integrations: SKAT REST API, VIES SOAP, PEPPOL AS4. Isolates the core from external API changes.")

    ContainerDb(db, "PostgreSQL", "PostgreSQL 16", "Immutable event ledger. Stores transactions, VAT returns, audit trail, corrections. Records are never deleted — corrections create new records. Flyway manages schema migrations.")

    Container(mcpServer, "MCP Server", "Node.js 20, TypeScript", "[Dev/AI tooling only — not part of production system] Provides business analyst and architect context documents to Claude Code AI agents via the Model Context Protocol.")
  }

  Rel(business, api, "Submits transactions, files returns, views VAT position", "HTTPS/REST")

  Rel(api, taxEngine, "Classifies transactions, calculates VAT, determines reverse charge", "In-process Java call")
  Rel(api, skatClient, "Delegates SKAT submission, VIES validation, PEPPOL dispatch", "In-process Java call")
  Rel(api, db, "Persists transactions, VAT returns, audit events", "JDBC/JOOQ")

  Rel(skatClient, skat, "Submits VAT returns, queries status", "HTTPS/REST")
  Rel(skatClient, vies, "Validates EU VAT numbers", "HTTPS/SOAP")
  Rel(skatClient, peppol, "Sends and receives e-invoices", "HTTPS/AS4")

  UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

---

## Container Responsibilities Summary

| Container | Type | Key Responsibility |
|---|---|---|
| REST API | Spring Boot app | Request handling, orchestration, Bean Validation |
| Tax Engine | Java library (in-process) | VAT calculation, classification, period rules |
| SKAT Client | Spring WebClient adapter | External API isolation (SKAT, VIES, PEPPOL) |
| PostgreSQL | Relational database | Immutable event ledger, Flyway migrations |
| MCP Server | Node.js tool (dev only) | AI agent context — not production |

## Notes

- The **Tax Engine** has **zero infrastructure dependencies** — it depends only on `core-domain`. This enables testing without a database, Spring context, or network.
- The **SKAT Client** is the sole point of contact with all external authorities — the Anti-Corruption Layer pattern.
- The **MCP Server** is excluded from all Kubernetes resource planning and production SLAs.
- All Spring Boot services expose `/actuator/health/liveness` and `/actuator/health/readiness` for Kubernetes probes.
