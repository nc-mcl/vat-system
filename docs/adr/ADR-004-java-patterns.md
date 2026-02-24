# ADR-004: Java 21 Design Patterns for the Domain Model

## Status
Accepted

## Context
The system is implemented in Java 21 with Spring Boot 3.3. This ADR documents the Java-specific design
decisions made in the `core-domain` module, which has zero framework dependencies and must remain
compilable as plain Java 21.

---

## Decision 1: Java Records for Domain Objects

### Decision
All domain value objects and aggregates are Java records (e.g. `MonetaryAmount`, `Transaction`,
`TaxPeriod`, `VatReturn`).

### Rationale
- **Value semantics by default** — records generate `equals()`, `hashCode()`, and `toString()`
  based on all components. Domain objects with identical field values are identical.
- **Immutability** — record components are final; there are no setters. This aligns with the
  audit-first principle: once created, a domain object is never modified in place.
- **Conciseness** — a record declaration is 1–3 lines vs. 20–30 lines of Lombok or manual boilerplate.
- **No Lombok needed** — records replace `@Value`, `@Data`, and `@Builder` for domain objects.
  Lombok is avoided in `core-domain` and `tax-engine` to keep the domain model self-contained.
- **Pattern matching** — records work seamlessly with Java 21's pattern matching in `switch` expressions.

### Consequence
Lombok may still be used in `api` and `persistence` modules for DTOs and JPA entities where
mutable or builder patterns are genuinely needed.

---

## Decision 2: Sealed Interfaces for Error Types

### Decision
Domain error types use sealed interfaces with record variants (e.g. `VatRuleError`).

```java
public sealed interface VatRuleError
        permits VatRuleError.UnknownTaxCode,
                VatRuleError.InvalidTransaction,
                VatRuleError.MissingVatNumber,
                VatRuleError.PeriodAlreadyFiled {

    record UnknownTaxCode(String code) implements VatRuleError {}
    record InvalidTransaction(String reason) implements VatRuleError {}
    record MissingVatNumber(String counterpartyId) implements VatRuleError {}
    record PeriodAlreadyFiled(UUID periodId) implements VatRuleError {}
}
```

### Rationale
- **Exhaustive pattern matching** — the `permits` clause tells the Java compiler exactly which
  subtypes exist. A `switch` expression over a sealed type produces a compile-time error if a case
  is missing, preventing unhandled error conditions.
- **No `instanceof` chains** — sealed types with pattern matching replace brittle if-instanceof chains.
- **Domain-expressiveness** — each error variant carries precisely the data needed for that error.
  There is no need for a generic error code + message string.
- **No exceptions for domain rules** — throwing exceptions for expected business rule violations
  (e.g. period already filed) mixes control flow with error reporting. Sealed error types are
  part of the method signature and cannot be silently ignored.

---

## Decision 3: `long` Øre Over `BigDecimal` for Monetary Values

### Decision
All monetary amounts are stored as `long` values representing the smallest currency unit (øre for DKK).
`BigDecimal` and floating-point types (`double`, `float`) are forbidden for monetary storage.

### Rationale
- **Exactness** — integer arithmetic is always exact. `BigDecimal` can still produce rounding errors
  if used incorrectly (wrong rounding mode, wrong scale). `long` arithmetic has no rounding errors
  at all for integer-unit currencies.
- **Performance** — `long` arithmetic is approximately 10× faster than `BigDecimal`. For a system
  that processes thousands of transactions per period, this matters.
- **Sufficiency** — Danish VAT law requires precision to the øre (1/100 DKK = 0.01 DKK).
  Two decimal places of currency precision is the maximum required; `long` øre is exactly sufficient.
- **Simplicity** — no need to manage `MathContext`, `RoundingMode`, or scale. All intermediate
  calculations stay in øre; only display formatting converts to DKK.
- **Basis points for rates** — VAT rates are stored as basis points (`long`), e.g. 2500 = 25.00%.
  Rate application: `vatOere = amountOere * rateInBasisPoints / 10_000L` (integer division, truncating).

### Consequence
- Display formatting is the only place that divides by 100 (øre → DKK). This is done in `toString()`
  methods and DTO formatters — never in business logic.
- Rate tables use `long` basis points throughout. The compiler enforces this since `long`
  is incompatible with `double` without an explicit cast.

---

## Decision 4: No Lombok on Domain Objects

### Decision
Lombok annotations are not used in `core-domain` or `tax-engine`. Records provide all the
boilerplate reduction needed.

### Rationale
- Java records already provide: constructor, getters (component accessors), `equals()`, `hashCode()`,
  `toString()`. There is nothing left for Lombok to do on domain objects.
- Removing Lombok from the domain modules eliminates a build-time annotation processor dependency,
  keeping the modules truly framework-free.
- Records are a Java language feature — they work with all Java tooling (IDEs, debuggers, serializers)
  without annotation processing.

### Consequence
Lombok may still be used in `api` and `persistence` for DTOs, `@Builder` patterns on complex
request objects, and JPA entities where records are not suitable (JPA requires mutable state).

---

## Decision 5: Virtual Threads for SKAT API Calls

### Decision
Spring Boot 3.3 virtual threads (`spring.threads.virtual.enabled=true`) are used for all I/O,
including SKAT API calls in the `skat-client` module.

### Rationale
- **SKAT API latency** — The SKAT REST API has variable latency (50–500ms typical). With platform
  threads, each in-flight SKAT request holds an OS thread. With virtual threads, thousands of
  concurrent SKAT calls can be handled with a handful of carrier threads.
- **Spring Boot 3.3 native support** — enabling virtual threads is a single configuration line.
  No reactive programming model (WebFlux) is required, avoiding the complexity of reactive APIs
  while achieving similar throughput.
- **WebClient compatibility** — Spring WebClient already supports virtual threads when virtual
  thread mode is enabled. No code changes are needed in `skat-client`.
- **Tomcat embedded** — Spring Boot 3.3 with virtual threads switches Tomcat's thread pool to
  virtual threads automatically. Each HTTP request gets its own lightweight virtual thread.

### Consequence
- `skat-client` uses `WebClient` (Spring's reactive HTTP client) in synchronous style — blocking
  calls on a virtual thread are fine because the virtual thread is cheap to park.
- Thread-local variables work correctly with virtual threads in Java 21.
- The `persistence` module's database connection pool (HikariCP) is safe with virtual threads;
  HikariCP pins virtual threads only during `synchronized` blocks, which are absent from JOOQ-generated code.

---

## Decision 6: OIOUBL 2.1 Phase-Out (ViDA Transition)

### Decision
OIOUBL 2.1 (the Danish e-invoicing format) is supported in Phase 1 but will be superseded by
PEPPOL BIS 3.0 as the mandatory format from **May 15, 2026**, when Danish authorities complete
the OIOUBL phase-out.

### Rationale
- Denmark's NemHandel network is migrating all B2G (business-to-government) e-invoicing to
  PEPPOL BIS 3.0 by May 15, 2026.
- The `skat-client` module's PEPPOL adapter targets BIS 3.0 from the start.
- OIOUBL 2.1 support is retained in Phase 1 for backward compatibility with existing integrations.

### Consequence
- Phase 2 work includes removing OIOUBL 2.1 support from `skat-client` after May 15, 2026.
- All new integration work targets PEPPOL BIS 3.0 only.
- The `skat-client` module documents both formats and the migration timeline in its README.

---

## Summary

| Pattern | Where Applied | Key Benefit |
|---|---|---|
| Java records | `core-domain` domain types | Immutability, value semantics, conciseness |
| Sealed interfaces | `VatRuleError`, `Result<T>` | Exhaustive error handling, no unchecked exceptions |
| `long` øre | All monetary fields | Exact arithmetic, no rounding errors |
| No Lombok | `core-domain`, `tax-engine` | Framework-free, cleaner domain |
| Virtual threads | `api`, `skat-client` | High-throughput I/O without reactive complexity |
| PEPPOL BIS 3.0 | `skat-client` | Mandatory post-May 2026, ViDA alignment |
