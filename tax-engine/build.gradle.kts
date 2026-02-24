// tax-engine: pure Java domain logic — no infrastructure dependencies.
// Implements VAT rate calculation, classification, and reverse charge rules.
// Must never import Spring, JOOQ, or any persistence/web framework.

dependencies {
    implementation(project(":core-domain"))
}
