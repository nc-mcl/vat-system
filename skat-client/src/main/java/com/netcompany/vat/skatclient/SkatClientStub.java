package com.netcompany.vat.skatclient;

import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.skatclient.config.SkatClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Phase 1 simulated SKAT client.
 *
 * <p>Returns configurable responses without connecting to the real SKAT API.
 * Controlled by the {@code skat.stub.response} property:
 * <ul>
 *   <li>{@code ACCEPTED} (default) — simulates a successful filing acceptance</li>
 *   <li>{@code REJECTED} — simulates SKAT rejecting the return with a validation error</li>
 *   <li>{@code UNAVAILABLE} — throws {@link SkatUnavailableException} to test error handling</li>
 * </ul>
 *
 * <p><strong>Phase 2 upgrade:</strong> Replace this bean with {@code SkatClientImpl}
 * in {@code SkatClientConfiguration} and provide real SKAT API credentials via
 * {@code SKAT_API_KEY} and {@code SKAT_BASE_URL} environment variables.
 *
 * @see SkatClientProperties
 */
public class SkatClientStub implements SkatClient {

    private static final Logger log = LoggerFactory.getLogger(SkatClientStub.class);

    private final SkatClientProperties properties;

    public SkatClientStub(SkatClientProperties properties) {
        this.properties = properties;
        log.warn("=================================================================");
        log.warn("SKAT stub active — not connected to real SKAT API");
        log.warn("Stub response mode: {}", properties.getStub().getResponse().toUpperCase());
        log.warn("Set SKAT_STUB_RESPONSE=ACCEPTED|REJECTED|UNAVAILABLE to change");
        log.warn("=================================================================");
    }

    @Override
    public SkatSubmissionResult submitReturn(VatReturn vatReturn) {
        String mode = properties.getStub().getResponse().toUpperCase();

        if ("UNAVAILABLE".equals(mode)) {
            throw new SkatUnavailableException(
                    "SKAT is unavailable (stub simulation). Set SKAT_STUB_RESPONSE=ACCEPTED to test success path.");
        }

        SubmissionStatus status = SubmissionStatus.valueOf(mode);
        String reference = "SKAT-STUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

        String message = switch (status) {
            case ACCEPTED -> "Momsangivelse modtaget og godkendt (stub simulation). Reference: " + reference;
            case REJECTED -> "Momsangivelse afvist: feltvalidering fejlet DK-VAT-001 — "
                    + "outputVatAmount skal være positiv (stub simulation)";
            case PENDING  -> "Momsangivelse modtaget og afventer behandling (stub simulation). Reference: " + reference;
        };

        log.info("SKAT stub: submitted return {} → status={} reference={}", vatReturn.id(), status, reference);

        return new SkatSubmissionResult(reference, status, message, Instant.now());
    }

    @Override
    public SkatSubmissionResult getSubmissionStatus(String skatReference) {
        log.info("SKAT stub: status query for reference {}", skatReference);
        return new SkatSubmissionResult(
                skatReference,
                SubmissionStatus.ACCEPTED,
                "Status retrieved (stub simulation)",
                Instant.now()
        );
    }
}
