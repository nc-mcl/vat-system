// skat-client: anti-corruption layer for all external authority integrations.
// Adapts SKAT REST API, VIES VAT number validation, and PEPPOL BIS 3.0 e-invoice
// behind the AuthorityApiClient interface defined in core-domain.
// Uses Spring WebClient backed by virtual threads for non-blocking HTTP I/O.

plugins {
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    implementation(project(":core-domain"))

    // Spring WebClient for SKAT and VIES HTTP calls
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Jackson for SKAT JSON payloads
    implementation("org.springframework.boot:spring-boot-starter-json")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // WireMock for stubbing SKAT sandbox API in integration tests
    testImplementation("org.wiremock:wiremock-standalone:3.5.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
}
