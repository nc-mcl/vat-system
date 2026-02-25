package com.netcompany.vat.skatclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the VIES VAT number validation client.
 *
 * <p>Phase 1: No active configuration (stub uses structural pattern only).
 * Phase 2: {@code baseUrl} and {@code timeoutSeconds} are used by {@code ViesClientImpl}.
 *
 * <p>All values can be overridden via environment variables:
 * <ul>
 *   <li>{@code VIES_BASE_URL} — overrides {@code vies.base-url}</li>
 *   <li>{@code VIES_TIMEOUT_SECONDS} — overrides {@code vies.timeout-seconds}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "vies")
public class ViesClientProperties {

    /** VIES REST API base URL. Used in Phase 2. */
    private String baseUrl = "https://ec.europa.eu/taxation_customs/vies/rest-api";

    /** HTTP timeout in seconds for VIES API calls. Used in Phase 2. */
    private int timeoutSeconds = 10;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
