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

    // Spring MVC slice tests + Mockito
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
