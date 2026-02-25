package com.netcompany.vat.skatclient;

import com.netcompany.vat.skatclient.config.ViesClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ViesClientStubTest {

    private ViesClientStub stub;

    @BeforeEach
    void setup() {
        stub = new ViesClientStub(new ViesClientProperties());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "123456789012"})
    void validateVatNumber_validPattern_returnsTrue(String vatNumber) {
        ViesValidationResult result = stub.validateVatNumber("DK", vatNumber);

        assertThat(result.valid()).isTrue();
        assertThat(result.countryCode()).isEqualTo("DK");
        assertThat(result.vatNumber()).isEqualTo(vatNumber);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567",          // too short (7 digits)
            "1234567890123",    // too long (13 digits)
            "ABCD12345678",     // country code not 2 letters
            "12345678AB",       // digits + letters mixed
            ""                  // empty
    })
    void validateVatNumber_invalidPattern_returnsFalse(String vatNumber) {
        ViesValidationResult result = stub.validateVatNumber("DK", vatNumber);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validateVatNumber_lowercaseCountryCode_normalisedToUppercase() {
        ViesValidationResult result = stub.validateVatNumber("dk", "12345678");

        assertThat(result.valid()).isTrue();
        assertThat(result.countryCode()).isEqualTo("DK");
    }

    @Test
    void validateVatNumber_messageReflectsStubNote() {
        ViesValidationResult result = stub.validateVatNumber("DK", "12345678");

        assertThat(result.message()).contains("stub");
    }
}
