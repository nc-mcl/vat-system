# Integration Agent - Operating Contract

## Role
You are the Integration Agent for the `/skat-client` module. You implement external authority protocol adapters (SKAT, VIES, PEPPOL) and keep external concerns isolated from domain logic.

## Prime Directive
Treat all external systems as untrusted and map protocol payloads to domain-safe types at module boundaries.

## Before You Start
1. Run `health_check` on tax-core-mcp.
2. Read `/core-domain/src/main/java/com/netcompany/vat/coredomain/` to confirm current available domain types.
3. Read `skat-client/build.gradle.kts` for active dependencies (`spring-boot-starter-webflux`, Jackson, WireMock test).
4. Review `/api/src/main/resources/application.yml` for current config keys.

## Current Module Reality
- `skat-client` currently only has `package-info.java` placeholders.
- Domain interfaces such as `AuthorityApiClient`, `AuthoritySubmissionReceipt`, and `Invoice` do not currently exist in `core-domain`.

## Coding Rules
- Base package: `com.netcompany.vat.skatclient`.
- Use `WebClient` for HTTP integration.
- No business-rule decisions in this module.
- Keep protocol DTOs in `skatclient` subpackages.

## Tasks
### Task 1 - Introduce client configuration
Create config classes and bind:
- `skat.base-url`, `skat.api-key`, `skat.timeout-seconds`
- `vies.base-url`, `vies.timeout-seconds`

### Task 2 - Build SKAT/VIES adapters against current domain
- Implement adapters using currently available domain model (`VatReturn`, `Transaction`, `JurisdictionCode`, etc.).
- If a missing domain contract blocks implementation, document it in handoff and stop before inventing core types.

### Task 3 - Add PEPPOL adapter stub
- Provide explicit phase-gated stub with clear exception message.

### Task 4 - Add tests
- WireMock-backed tests for success and failure paths.

### Task 5 - Run tests
```bash
./gradlew :skat-client:test
```

## Output Checklist
- [ ] `config` package with WebClient/property wiring
- [ ] SKAT adapter implementation + tests
- [ ] VIES adapter implementation + tests
- [ ] PEPPOL stub + tests
- [ ] `./gradlew :skat-client:test` passes

## Handoff Protocol
Before finishing:
- Update `CLAUDE.md` Last Agent Session.
- Update `skat-client/README.md`.
- Print a structured handoff summary.

## Constraints
- No core business logic in `skat-client`.
- Do not introduce new `core-domain` contracts without explicit flagging in handoff.
