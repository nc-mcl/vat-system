package com.netcompany.vat.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.audit.JooqAuditLogger;
import com.netcompany.vat.persistence.repository.*;
import com.netcompany.vat.persistence.repository.jooq.*;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the persistence module.
 *
 * <p>{@code DSLContext} is auto-configured by {@code spring-boot-starter-jooq} using
 * the {@code spring.datasource.*} and {@code spring.jooq.sql-dialect} properties.
 * This class wires the repository implementations and audit logger as Spring beans.
 *
 * <p>The consuming application (api module) only needs to import or component-scan
 * {@code com.netcompany.vat.persistence} to activate all repository beans.
 */
@Configuration
@ComponentScan(basePackages = "com.netcompany.vat.persistence")
public class PersistenceConfiguration {

    /**
     * Provides a default {@link ObjectMapper} if the application context does not already
     * define one.  The api module's Spring Boot auto-configuration will typically supply
     * a fully configured instance; this fallback covers standalone use and integration tests.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public TaxPeriodRepository taxPeriodRepository(DSLContext ctx) {
        return new JooqTaxPeriodRepository(ctx);
    }

    @Bean
    public TransactionRepository transactionRepository(DSLContext ctx) {
        return new JooqTransactionRepository(ctx);
    }

    @Bean
    public VatReturnRepository vatReturnRepository(DSLContext ctx, ObjectMapper objectMapper) {
        return new JooqVatReturnRepository(ctx, objectMapper);
    }

    @Bean
    public CounterpartyRepository counterpartyRepository(DSLContext ctx) {
        return new JooqCounterpartyRepository(ctx);
    }

    @Bean
    public CorrectionRepository correctionRepository(DSLContext ctx) {
        return new JooqCorrectionRepository(ctx);
    }

    @Bean
    public AuditLogger auditLogger(DSLContext ctx, ObjectMapper objectMapper) {
        return new JooqAuditLogger(ctx, objectMapper);
    }
}
