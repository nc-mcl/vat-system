package com.netcompany.vat.skatclient;

import com.netcompany.vat.skatclient.config.ViesClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Phase 1 simulated VIES VAT number validation client.
 *
 * <p>Validates VAT numbers against a structural pattern without connecting to the
 * real VIES service. A number is considered valid if it matches the pattern
 * {@code [A-Z]{2}[0-9]{8,12}} (two uppercase country code letters followed by 8–12 digits).
 *
 * <p><strong>Phase 2 upgrade:</strong> Replace this bean with {@code ViesClientImpl}
 * backed by Spring WebClient against the EU VIES REST API.
 */
public class ViesClientStub implements ViesClient {

    private static final Logger log = LoggerFactory.getLogger(ViesClientStub.class);

    /** Pattern for structural VAT number validation: CC + 8–12 digits. */
    private static final Pattern VAT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{8,12}$");

    public ViesClientStub(ViesClientProperties properties) {
        log.warn("=================================================================");
        log.warn("VIES stub active — not connected to real VIES API");
        log.warn("Validation uses structural pattern only: [A-Z]{{2}}[0-9]{{8,12}}");
        log.warn("=================================================================");
    }

    @Override
    public ViesValidationResult validateVatNumber(String countryCode, String vatNumber) {
        String combined = countryCode.toUpperCase() + vatNumber;
        boolean valid = VAT_PATTERN.matcher(combined).matches();
        String message = valid
                ? "VAT number structurally valid (stub — VIES not consulted)"
                : "VAT number invalid: does not match pattern [A-Z]{2}[0-9]{8,12} (stub)";

        log.debug("VIES stub: validation for {}{} → valid={}", countryCode, vatNumber, valid);

        return new ViesValidationResult(valid, countryCode.toUpperCase(), vatNumber, message);
    }
}
