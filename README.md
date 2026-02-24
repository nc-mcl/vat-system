# VAT System

## Project Overview
VAT System is a multi-jurisdiction VAT platform with a jurisdiction-agnostic core and Denmark (`DK`) as the first plugin implementation. The backend stack is Java 21 + Spring Boot 3.3 with a Gradle multi-module layout. The repository also contains a separate TypeScript MCP server used for agent context tooling.

## Architecture
The architecture follows the current `CLAUDE.md` model:
- Jurisdiction-agnostic core domain and plugin SPI (`JurisdictionPlugin`)
- Danish rules implemented as a jurisdiction plugin (`DkJurisdictionPlugin`)
- Pure business rules in `tax-engine` (no infrastructure dependencies)
- Persistence concerns in `persistence` (JOOQ + Flyway)
- API and orchestration in `api` (Spring Boot)
- External authority integration in `skat-client` (SKAT, VIES, PEPPOL)
- Separate `mcp-server` (TypeScript/Node.js), not part of the Java backend runtime

Project phases:
- Phase 1 (MVP): Danish VAT core capabilities
- Phase 2 (ViDA): DRR + extended OSS + platform rules
- Phase 3: Additional jurisdiction plugins without core rewrites

## Module Structure
Gradle submodules (from `settings.gradle.kts`):
- `core-domain`
- `tax-engine`
- `persistence`
- `api`
- `skat-client`

Non-Gradle module:
- `mcp-server` (TypeScript MCP tooling)

## Running Locally
Prerequisites:
- JDK 21
- Node.js 18+

Build all Java modules:
```bash
./gradlew clean build
```

Run all Java tests:
```bash
./gradlew test
```

Run only the API module:
```bash
./gradlew :api:bootRun
```

Run MCP server (separate process):
```bash
cd mcp-server
npm install
npm run dev
```

## Running with Docker
Current repository state:
- No `Dockerfile` is present.
- No root `docker-compose.yml` is present.

Docker-based local/runtime commands are therefore not currently available in this repository.

## Deploying to Kubernetes
Current repository state:
- No Kubernetes manifests are present under `/k8s` or `/infrastructure/k8s`.
- Existing infrastructure directories are `/infrastructure/api`, `/infrastructure/db`, and `/infrastructure/messaging`.

Kubernetes deployment commands are therefore not currently available in this repository.

## Environment Variables
Primary variables used by runtime modules:

| Variable | Required | Default | Used By | Description |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes (integration runtime) | - | `api`, `persistence` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes (integration runtime) | - | `api`, `persistence` | PostgreSQL username |
| `SPRING_DATASOURCE_PASSWORD` | Yes (integration runtime) | - | `api`, `persistence` | PostgreSQL password |
| `SKAT_BASE_URL` | No | `https://api-sandbox.skat.dk` | `api`, `skat-client` | SKAT API base URL |
| `SKAT_API_KEY` | Yes (authenticated SKAT calls) | empty | `api`, `skat-client` | SKAT API credential |
| `VIES_BASE_URL` | No | EU VIES endpoint | `api`, `skat-client` | VIES validation endpoint |

## License Policy
This repository enforces an open-source-only dependency policy (see `ROLE_CONTEXT_POLICY.md`):
- Allowed: Apache 2.0, MIT, BSD, EPL, LGPL (with distribution caution)
- Forbidden: proprietary/commercial, GPL, AGPL, royalty-triggering licenses

Before adding dependencies, verify license compatibility.

## Development Workflow
- Use Gradle as the source of truth for Java module builds (`./gradlew ...`).
- Keep domain logic framework-free in `core-domain` and `tax-engine`.
- Follow agent handoff protocol in `ROLE_CONTEXT_POLICY.md`:
  - Update `CLAUDE.md` last session block
  - Update component READMEs for touched areas
  - Verify successor readiness
  - Provide handoff summary
- For documentation changes, keep markdown aligned with actual files and module/package names.
