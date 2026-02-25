package com.netcompany.vat.skatclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the SKAT client.
 *
 * <p>Phase 1: Only {@code stub.response} is active.
 * Phase 2: {@code baseUrl} and {@code apiKey} are used by {@code SkatClientImpl}.
 *
 * <p>All values can be overridden via environment variables:
 * <ul>
 *   <li>{@code SKAT_BASE_URL} — overrides {@code skat.base-url}</li>
 *   <li>{@code SKAT_API_KEY} — overrides {@code skat.api-key}</li>
 *   <li>{@code SKAT_TIMEOUT_SECONDS} — overrides {@code skat.timeout-seconds}</li>
 *   <li>{@code SKAT_STUB_RESPONSE} — overrides {@code skat.stub.response}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "skat")
public class SkatClientProperties {

    /** SKAT API base URL. Used in Phase 2. */
    private String baseUrl = "https://api-sandbox.skat.dk";

    /** SKAT API key. Used in Phase 2. Never log or expose. */
    private String apiKey = "";

    /** HTTP timeout in seconds for SKAT API calls. Used in Phase 2. */
    private int timeoutSeconds = 30;

    /** Phase 1 stub configuration. */
    private Stub stub = new Stub();

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public Stub getStub() { return stub; }
    public void setStub(Stub stub) { this.stub = stub; }

    /**
     * Stub-specific configuration.
     */
    public static class Stub {

        /**
         * Controls stub behaviour. Values: ACCEPTED | REJECTED | UNAVAILABLE.
         * Defaults to ACCEPTED (simulates successful SKAT filing).
         */
        private String response = "ACCEPTED";

        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
    }
}
