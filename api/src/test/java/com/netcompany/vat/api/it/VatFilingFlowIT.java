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
 * Phase 1 proof-of-life integration test.
 *
 * <p>Exercises the full VAT filing flow end-to-end against a real PostgreSQL database
 * with the SKAT stub configured for ACCEPTED responses (default):
 * <ol>
 *   <li>POST /api/v1/periods → 201</li>
 *   <li>POST /api/v1/transactions ×3 (mix of STANDARD and ZERO_RATED) → 201 each</li>
 *   <li>POST /api/v1/returns/assemble → 201, verify netVat is correct</li>
 *   <li>POST /api/v1/returns/{id}/submit → 202, status=ACCEPTED, skatReference non-null</li>
 *   <li>GET /api/v1/returns/{id} → verify status=ACCEPTED, skatReference non-null</li>
 * </ol>
 *
 * <p>Requires Docker to be accessible from the JVM process. On Windows with Docker Desktop,
 * enable "Expose daemon on tcp://localhost:2375" or ensure the named pipe is accessible.
 * In CI (Linux) this test runs automatically. The test skips gracefully if Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class VatFilingFlowIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("vatdb_test")
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

    @Test
    @SuppressWarnings("unchecked")
    void fullVatFilingFlow_happyPath() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ── Step 1: Open a Q1 2026 filing period ─────────────────────────────
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
                new HttpEntity<>(openPeriodBody, headers),
                Map.class);
        assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String periodId = (String) periodResp.getBody().get("id");
        assertThat(periodId).isNotNull();
        assertThat(periodResp.getBody().get("status")).isEqualTo("OPEN");

        // ── Step 2a: Standard-rated sale (100,000 øre = 1,000 DKK, 25% VAT = 25,000 øre) ──
        String tx1Body = """
                {
                    "periodId": "%s",
                    "taxCode": "STANDARD",
                    "amountExclVat": 100000,
                    "transactionDate": "2026-01-15",
                    "description": "Domestic sale — standard rate"
                }
                """.formatted(periodId);
        ResponseEntity<Map> tx1Resp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(tx1Body, headers),
                Map.class);
        assertThat(tx1Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tx1Resp.getBody().get("taxCode")).isEqualTo("STANDARD");

        // ── Step 2b: Another standard-rated sale (200,000 øre, VAT = 50,000 øre) ──
        String tx2Body = """
                {
                    "periodId": "%s",
                    "taxCode": "STANDARD",
                    "amountExclVat": 200000,
                    "transactionDate": "2026-02-10",
                    "description": "Domestic sale 2 — standard rate"
                }
                """.formatted(periodId);
        ResponseEntity<Map> tx2Resp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(tx2Body, headers),
                Map.class);
        assertThat(tx2Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // ── Step 2c: Zero-rated export (150,000 øre, VAT = 0) ────────────────
        String tx3Body = """
                {
                    "periodId": "%s",
                    "taxCode": "ZERO_RATED",
                    "amountExclVat": 150000,
                    "transactionDate": "2026-03-05",
                    "description": "Export to EU — zero rated"
                }
                """.formatted(periodId);
        ResponseEntity<Map> tx3Resp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(tx3Body, headers),
                Map.class);
        assertThat(tx3Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // ── Step 3: Assemble return ───────────────────────────────────────────
        // Expected: outputVat = 25,000 + 50,000 = 75,000 øre
        //           inputVat  = 0 (no purchases in this scenario)
        //           netVat    = 75,000 øre → PAYABLE
        String assembleBody = """
                {
                    "periodId": "%s",
                    "jurisdictionCode": "DK"
                }
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers),
                Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> returnBody = assembleResp.getBody();
        String returnId = (String) returnBody.get("id");
        assertThat(returnId).isNotNull();
        assertThat(returnBody.get("status")).isEqualTo("DRAFT");
        assertThat(returnBody.get("resultType")).isEqualTo("PAYABLE");

        // netVat should be 75,000 øre (25,000 + 50,000 from two STANDARD sales)
        Object netVatObj = returnBody.get("netVat");
        long netVat = netVatObj instanceof Number n ? n.longValue() : Long.parseLong(netVatObj.toString());
        assertThat(netVat).isEqualTo(75_000L);

        // ── Step 4: Submit the return — SKAT stub responds ACCEPTED ──────────
        ResponseEntity<Map> submitResp = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers),
                Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submitResp.getBody().get("status")).isEqualTo("ACCEPTED");
        assertThat(submitResp.getBody().get("skatReference")).isNotNull();

        // ── Step 5: Verify GET returns ACCEPTED with skatReference ────────────
        ResponseEntity<Map> getResp = restTemplate.getForEntity(
                base + "/api/v1/returns/" + returnId,
                Map.class);
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody().get("status")).isEqualTo("ACCEPTED");
        assertThat(getResp.getBody().get("skatReference")).isNotNull();
        assertThat(getResp.getBody().get("resultType")).isEqualTo("PAYABLE");

        // ── Bonus: Verify period is locked (FILED) after return assembly ──────
        ResponseEntity<Map> periodGetResp = restTemplate.getForEntity(
                base + "/api/v1/periods/" + periodId,
                Map.class);
        assertThat(periodGetResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(periodGetResp.getBody().get("status")).isEqualTo("FILED");
    }

    @Test
    void submitReturn_calledTwice_returns409() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Open a period
        String openPeriodBody = """
                {
                    "jurisdictionCode": "DK",
                    "periodStart": "2026-04-01",
                    "periodEnd": "2026-06-30",
                    "filingCadence": "QUARTERLY"
                }
                """;
        ResponseEntity<Map> periodResp = restTemplate.postForEntity(
                base + "/api/v1/periods",
                new HttpEntity<>(openPeriodBody, headers), Map.class);
        String periodId = (String) periodResp.getBody().get("id");

        // Assemble
        String assembleBody = """
                {"periodId": "%s", "jurisdictionCode": "DK"}
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers), Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String returnId = (String) assembleResp.getBody().get("id");

        // Submit once — 202 (SKAT stub → ACCEPTED)
        ResponseEntity<Map> submit1 = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers), Map.class);
        assertThat(submit1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submit1.getBody().get("status")).isEqualTo("ACCEPTED");

        // Submit again — 409 (status is ACCEPTED, not DRAFT)
        ResponseEntity<Map> submit2 = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers), Map.class);
        assertThat(submit2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
