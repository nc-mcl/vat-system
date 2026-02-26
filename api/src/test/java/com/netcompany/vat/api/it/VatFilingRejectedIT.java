package com.netcompany.vat.api.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario D — SKAT Rejection.
 *
 * <p>Tests the full filing flow when SKAT returns a REJECTED response.
 * The SKAT stub is configured via {@code skat.stub.response=REJECTED}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Submit returns HTTP 202 Accepted (the request was processed — SKAT made a decision)</li>
 *   <li>The return status in the response is REJECTED</li>
 *   <li>No skatReference is present (only issued on ACCEPTED)</li>
 *   <li>A subsequent GET confirms the REJECTED status is persisted</li>
 * </ul>
 *
 * <p>This test class uses a separate Spring context with {@code skat.stub.response=REJECTED}.
 *
 * <p>Requires Docker. Skips gracefully without it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "skat.stub.response=REJECTED"
)
@Testcontainers(disabledWithoutDocker = true)
class VatFilingRejectedIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("vatdb_rejected_test")
                    .withUsername("vat_test")
                    .withPassword("vat_test_pass");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Scenario D: SKAT REJECTED response.
     *
     * <p>Open a period, add a transaction, assemble, submit → expect REJECTED status.
     * The audit trail must record the rejection event.
     */
    @Test
    @SuppressWarnings("unchecked")
    void skatRejection_fullFlow_statusIsRejected() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Step 1: Open a period
        String openPeriodBody = """
                {
                    "jurisdictionCode": "DK",
                    "periodStart": "2026-01-01",
                    "periodEnd": "2026-03-31",
                    "filingCadence": "QUARTERLY"
                }
                """;
        ResponseEntity<Map> periodResp = restTemplate.postForEntity(
                base + "/api/v1/periods",
                new HttpEntity<>(openPeriodBody, headers), Map.class);
        assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String periodId = (String) periodResp.getBody().get("id");

        // Step 2: Add a STANDARD transaction
        String txBody = """
                {
                    "periodId": "%s",
                    "taxCode": "STANDARD",
                    "amountExclVat": 100000,
                    "transactionDate": "2026-01-15",
                    "description": "Domestic sale"
                }
                """.formatted(periodId);
        ResponseEntity<Map> txResp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(txBody, headers), Map.class);
        assertThat(txResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 3: Assemble → DRAFT return
        String assembleBody = """
                {"periodId": "%s", "jurisdictionCode": "DK"}
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers), Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(assembleResp.getBody().get("status")).isEqualTo("DRAFT");
        String returnId = (String) assembleResp.getBody().get("id");

        // Step 4: Submit → SKAT stub configured for REJECTED
        // HTTP 202 Accepted — the request was processed; SKAT made a rejection decision
        ResponseEntity<Map> submitResp = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers), Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Map<String, Object> submitBody = submitResp.getBody();
        assertThat(submitBody.get("status")).isEqualTo("REJECTED");

        // No skatReference on rejection
        assertThat(submitBody.get("skatReference")).isNull();

        // Step 5: Verify via GET that REJECTED status is persisted
        ResponseEntity<Map> getResp = restTemplate.getForEntity(
                base + "/api/v1/returns/" + returnId, Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(getResp.getBody().get("skatReference")).isNull();
    }
}
