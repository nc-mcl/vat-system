// persistence: JOOQ data layer + Flyway migrations.
// Provides repository implementations for the immutable event ledger and all core aggregates.
// JOOQ DSL classes are generated from the PostgreSQL schema at build time via jooqGenerate task.

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

    // JOOQ — typesafe SQL generation from PostgreSQL schema
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // Flyway — versioned schema migrations with PostgreSQL-specific DDL support
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC driver
    runtimeOnly("org.postgresql:postgresql")

    // Integration testing with real PostgreSQL via Testcontainers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.testcontainers:postgresql:1.20.1")
}
