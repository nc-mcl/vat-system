package com.netcompany.vat.skatclient;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.skatclient.config.SkatClientProperties;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkatClientStubTest {

    private static VatReturn draftReturn() {
        return VatReturn.of(
                UUID.randomUUID(),
                JurisdictionCode.DK,
                UUID.randomUUID(),
                MonetaryAmount.ofOere(75_000L),
                MonetaryAmount.ofOere(0L),
                VatReturnStatus.DRAFT,
                Collections.emptyMap()
        );
    }

    private static SkatClientStub stubWith(String response) {
        SkatClientProperties props = new SkatClientProperties();
        props.getStub().setResponse(response);
        return new SkatClientStub(props);
    }

    @Test
    void submitReturn_defaultConfig_returnsAccepted() {
        SkatClientStub stub = stubWith("ACCEPTED");
        VatReturn vatReturn = draftReturn();

        SkatSubmissionResult result = stub.submitReturn(vatReturn);

        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.skatReference()).isNotNull();
        assertThat(result.skatReference()).startsWith("SKAT-STUB-");
        assertThat(result.message()).contains("stub");
        assertThat(result.processedAt()).isNotNull();
    }

    @Test
    void submitReturn_rejectedConfig_returnsRejected() {
        SkatClientStub stub = stubWith("REJECTED");
        VatReturn vatReturn = draftReturn();

        SkatSubmissionResult result = stub.submitReturn(vatReturn);

        assertThat(result.status()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(result.message()).contains("stub");
        assertThat(result.processedAt()).isNotNull();
    }

    @Test
    void submitReturn_unavailableConfig_throwsSkatUnavailableException() {
        SkatClientStub stub = stubWith("UNAVAILABLE");
        VatReturn vatReturn = draftReturn();

        assertThatThrownBy(() -> stub.submitReturn(vatReturn))
                .isInstanceOf(SkatUnavailableException.class)
                .hasMessageContaining("stub simulation");
    }

    @Test
    void getSubmissionStatus_alwaysReturnsAccepted() {
        SkatClientStub stub = stubWith("ACCEPTED");
        String reference = "SKAT-STUB-TEST123";

        SkatSubmissionResult result = stub.getSubmissionStatus(reference);

        assertThat(result.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result.skatReference()).isEqualTo(reference);
    }
}
