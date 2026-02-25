// persistence: JOOQ data layer + Flyway migrations.
// Provides repository implementations for the immutable event ledger and all core aggregates.
//
// JOOQ usage: this module uses JOOQ's dynamic SQL DSL (DSL.table() / DSL.field()) so
// compilation does not depend on a running database.  Type-safe code generation can be
// added later with the nu.studer.jooq Gradle plugin (version 9.x) once the schema is stable:
//   plugins { id("nu.studer.jooq") version "9.0" }
// Point the DDLDatabase at src/main/resources/db/migration and generate into
// src/main/generated/.

plugins {
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

// Register src/main/generated as a source set so that future JOOQ-generated classes
// (produced by the nu.studer.jooq plugin) are compiled automatically.
sourceSets {
    main {
        java {
            srcDir("src/main/generated")
        }
    }
}

dependencies {
    implementation(project(":core-domain"))

    // JOOQ — typesafe SQL generation from PostgreSQL schema.
    // Open source edition only (Apache 2.0).  Do NOT add jooq-pro.
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    // Flyway — versioned schema migrations with PostgreSQL-specific DDL support
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC driver
    runtimeOnly("org.postgresql:postgresql")

    // Jackson — JSONB serialisation for VatReturn.jurisdictionFields and audit payloads
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Structured JSON logging (required per DevOps handoff — Apache 2.0)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Integration testing with real PostgreSQL via Testcontainers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.testcontainers:postgresql:1.20.1")
}
