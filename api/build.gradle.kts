// api: Spring Boot application module — the deployable artifact.
// Exposes REST endpoints, applies Bean Validation at the controller boundary,
// and wires jurisdiction plugins via Spring dependency injection.

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":tax-engine"))
    implementation(project(":persistence"))
    implementation(project(":skat-client"))

    // Spring MVC + embedded Tomcat
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Jakarta Bean Validation with Hibernate Validator runtime
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring Boot Actuator — liveness/readiness probes + Prometheus metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Jackson parameter names module — enables constructor binding without @JsonProperty
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names")

    // Structured JSON logging (Apache 2.0)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // OpenAPI / Swagger UI (Apache 2.0)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Spring MVC slice tests + Mockito
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Testcontainers — PostgreSQL for integration tests (MIT)
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.testcontainers:postgresql:1.20.1")
}
