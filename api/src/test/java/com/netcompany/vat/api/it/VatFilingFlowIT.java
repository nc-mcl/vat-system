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
import static org.assertj.core.api.Assertions.assertThatObject;

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

    /**
     * Scenario B — NIL return (nulindberetning).
     *
     * <p>Phase 1 note: A true CLAIMABLE result (input VAT > output VAT) is not achievable
     * via the REST API in Phase 1 because the Transaction model does not carry a direction
     * flag (SALE/PURCHASE). STANDARD transactions are always treated as sales (output VAT only).
     * REVERSE_CHARGE contributes equal output and input (net zero). This test verifies the
     * Phase 1 current behaviour: EXEMPT-only period → NIL return (resultType=ZERO).
     *
     * <p>This is consistent with Danish VAT law: businesses providing only exempt supplies
     * (e.g. healthcare) must still file a zero return (nulindberetning).
     */
    @Test
    @SuppressWarnings("unchecked")
    void scenario_B_nilReturnExemptPeriod() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Open a period in H2 2026 (different from Scenario A to avoid period conflicts)
        String openPeriodBody = """
                {
                    "jurisdictionCode": "DK",
                    "periodStart": "2026-07-01",
                    "periodEnd": "2026-12-31",
                    "filingCadence": "SEMI_ANNUAL"
                }
                """;
        ResponseEntity<Map> periodResp = restTemplate.postForEntity(
                base + "/api/v1/periods",
                new HttpEntity<>(openPeriodBody, headers), Map.class);
        assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String periodId = (String) periodResp.getBody().get("id");

        // Add only EXEMPT transactions (medical services, healthcare)
        String tx1Body = """
                {
                    "periodId": "%s",
                    "taxCode": "EXEMPT",
                    "amountExclVat": 200000,
                    "transactionDate": "2026-08-15",
                    "description": "Medical consultation — ML §13(1)(1)"
                }
                """.formatted(periodId);
        ResponseEntity<Map> tx1Resp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(tx1Body, headers), Map.class);
        assertThat(tx1Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tx1Resp.getBody().get("vatAmount")).isEqualTo(0);

        String tx2Body = """
                {
                    "periodId": "%s",
                    "taxCode": "EXEMPT",
                    "amountExclVat": 150000,
                    "transactionDate": "2026-09-10",
                    "description": "Education service — ML §13(1)(3)"
                }
                """.formatted(periodId);
        restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(tx2Body, headers), Map.class);

        // Assemble — expect NIL return (resultType=ZERO, netVat=0)
        String assembleBody = """
                {"periodId": "%s", "jurisdictionCode": "DK"}
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers), Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = assembleResp.getBody();
        assertThat(body.get("resultType")).isEqualTo("ZERO");

        Object netVatObj = body.get("netVat");
        long netVat = netVatObj instanceof Number n ? n.longValue() : Long.parseLong(netVatObj.toString());
        assertThat(netVat).isEqualTo(0L);
        assertThat(body.get("outputVat")).isEqualTo(0);

        String returnId = (String) body.get("id");

        // Submit — SKAT stub returns ACCEPTED even for NIL returns
        ResponseEntity<Map> submitResp = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers), Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submitResp.getBody().get("status")).isEqualTo("ACCEPTED");
    }

    /**
     * Scenario C — Reverse charge (EU B2B service purchase).
     *
     * <p>A REVERSE_CHARGE transaction self-assesses both output VAT and equal input VAT,
     * resulting in net zero impact on the period (rubrik A services populated).
     * This verifies Rule 5.1 (B2B cross-border services → reverse charge).
     */
    @Test
    @SuppressWarnings("unchecked")
    void scenario_C_reverseChargeTransaction() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Open a new period for this scenario
        String openPeriodBody = """
                {
                    "jurisdictionCode": "DK",
                    "periodStart": "2026-10-01",
                    "periodEnd": "2026-12-31",
                    "filingCadence": "QUARTERLY"
                }
                """;
        ResponseEntity<Map> periodResp = restTemplate.postForEntity(
                base + "/api/v1/periods",
                new HttpEntity<>(openPeriodBody, headers), Map.class);
        assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String periodId = (String) periodResp.getBody().get("id");

        // Add a REVERSE_CHARGE transaction (B2B service from German supplier)
        // Base: 800,000 øre = 8,000 DKK; 25% VAT self-assessed = 200,000 øre
        String txBody = """
                {
                    "periodId": "%s",
                    "taxCode": "REVERSE_CHARGE",
                    "amountExclVat": 800000,
                    "transactionDate": "2026-10-15",
                    "description": "Software license from Germany — reverse charge"
                }
                """.formatted(periodId);
        ResponseEntity<Map> txResp = restTemplate.postForEntity(
                base + "/api/v1/transactions",
                new HttpEntity<>(txBody, headers), Map.class);
        assertThat(txResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Verify isReverseCharge=true on the transaction response
        Object isRcObj = txResp.getBody().get("isReverseCharge");
        assertThat(isRcObj).isNotNull();
        assertThat(Boolean.TRUE.equals(isRcObj)).isTrue();

        // VAT amount = 800,000 * 25% = 200,000 øre
        Object vatAmountObj = txResp.getBody().get("vatAmount");
        long vatAmount = vatAmountObj instanceof Number n ? n.longValue() : Long.parseLong(vatAmountObj.toString());
        assertThat(vatAmount).isEqualTo(200_000L);

        // Assemble — RC output = RC input → net VAT = 0 (ZERO result type)
        String assembleBody = """
                {"periodId": "%s", "jurisdictionCode": "DK"}
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers), Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> returnBody = assembleResp.getBody();
        // rubrikA should contain the RC base amount
        Object rubrikA = returnBody.get("rubrikA");
        long rubrikAVal = rubrikA instanceof Number n ? n.longValue() : Long.parseLong(rubrikA.toString());
        assertThat(rubrikAVal).isEqualTo(800_000L);

        // Output VAT = Input VAT = 200,000 → net = 0
        assertThat(returnBody.get("resultType")).isEqualTo("ZERO");

        String returnId = (String) returnBody.get("id");

        // Submit — ACCEPTED
        ResponseEntity<Map> submitResp = restTemplate.postForEntity(
                base + "/api/v1/returns/" + returnId + "/submit",
                new HttpEntity<>(null, headers), Map.class);
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(submitResp.getBody().get("status")).isEqualTo("ACCEPTED");
        assertThat(submitResp.getBody().get("skatReference")).isNotNull();
    }

    /**
     * Scenario D — Mixed STANDARD + ZERO_RATED (zero-rated export populates rubrikB).
     *
     * <p>Tests that zero-rated transactions go to rubrikB (EU sales) and don't
     * contribute output VAT, while STANDARD sales do produce output VAT.
     */
    @Test
    @SuppressWarnings("unchecked")
    void scenario_D_mixedStandardAndZeroRated_rubrikBPopulated() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String openPeriodBody = """
                {
                    "jurisdictionCode": "DK",
                    "periodStart": "2027-01-01",
                    "periodEnd": "2027-03-31",
                    "filingCadence": "QUARTERLY"
                }
                """;
        ResponseEntity<Map> periodResp = restTemplate.postForEntity(
                base + "/api/v1/periods",
                new HttpEntity<>(openPeriodBody, headers), Map.class);
        assertThat(periodResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String periodId = (String) periodResp.getBody().get("id");

        // STANDARD domestic sale: 400,000 øre base → 100,000 øre VAT
        String tx1Body = """
                {"periodId": "%s", "taxCode": "STANDARD",
                 "amountExclVat": 400000, "transactionDate": "2027-01-20",
                 "description": "Domestic sale"}
                """.formatted(periodId);
        restTemplate.postForEntity(base + "/api/v1/transactions",
                new HttpEntity<>(tx1Body, headers), Map.class);

        // ZERO_RATED export to EU: 500,000 øre base → 0 øre VAT, goes to rubrikB
        String tx2Body = """
                {"periodId": "%s", "taxCode": "ZERO_RATED",
                 "amountExclVat": 500000, "transactionDate": "2027-02-15",
                 "description": "EU export — intra-community supply"}
                """.formatted(periodId);
        restTemplate.postForEntity(base + "/api/v1/transactions",
                new HttpEntity<>(tx2Body, headers), Map.class);

        // Assemble
        String assembleBody = """
                {"periodId": "%s", "jurisdictionCode": "DK"}
                """.formatted(periodId);
        ResponseEntity<Map> assembleResp = restTemplate.postForEntity(
                base + "/api/v1/returns/assemble",
                new HttpEntity<>(assembleBody, headers), Map.class);
        assertThat(assembleResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> returnBody = assembleResp.getBody();

        // Output VAT = 100,000 (STANDARD only; ZERO_RATED adds no VAT)
        Object outputVat = returnBody.get("outputVat");
        long outputVatVal = outputVat instanceof Number n ? n.longValue() : Long.parseLong(outputVat.toString());
        assertThat(outputVatVal).isEqualTo(100_000L);

        // rubrikB should contain the zero-rated export value
        Object rubrikB = returnBody.get("rubrikB");
        long rubrikBVal = rubrikB instanceof Number n ? n.longValue() : Long.parseLong(rubrikB.toString());
        assertThat(rubrikBVal).isEqualTo(500_000L);

        assertThat(returnBody.get("resultType")).isEqualTo("PAYABLE");
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
