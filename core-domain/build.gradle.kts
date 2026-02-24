// core-domain: pure Java — no framework dependencies.
// Defines JurisdictionPlugin SPI, domain records, enums, and value objects.
// All other modules depend on this; it must not depend on any of them.

dependencies {
    // Jakarta Bean Validation API only — no runtime implementation.
    // Constraint annotations (@NotNull, etc.) on domain records are API-level contracts.
    // The implementation (Hibernate Validator) is provided by the api module at runtime.
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
}
