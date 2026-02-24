package com.netcompany.vat.taxengine;

import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.VatRuleError;
import com.netcompany.vat.domain.dk.DkJurisdictionPlugin;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RateResolver}.
 *
 * <p>Uses the {@link DkJurisdictionPlugin} as the reference implementation.
 * All expected values are derived from SKAT-verified rules (25% standard rate, etc.).
 */
@DisplayName("RateResolver")
class RateResolverTest {

    private RateResolver resolver;
    private JurisdictionPlugin dkPlugin;
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 1, 1);

    @BeforeEach
    void setUp() {
        resolver = new RateResolver();
        dkPlugin = new DkJurisdictionPlugin();
    }

    @Test
    @DisplayName("resolves DK standard rate as 2500 basis points (25%)")
    void resolveStandardRate() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.STANDARD, EFFECTIVE);

        assertInstanceOf(Result.Ok.class, result);
        assertEquals(2500L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("resolves DK zero rate as 0 basis points")
    void resolveZeroRatedRate() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.ZERO_RATED, EFFECTIVE);

        assertInstanceOf(Result.Ok.class, result);
        assertEquals(0L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("resolves DK exempt as -1 (not applicable) — not an error")
    void resolveExemptRate_isNegativeOneNotError() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.EXEMPT, EFFECTIVE);

        assertTrue(result.isOk(), "EXEMPT is a valid code — not an error");
        assertEquals(-1L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("resolves DK out-of-scope as -1 (not applicable)")
    void resolveOutOfScopeRate() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.OUT_OF_SCOPE, EFFECTIVE);

        assertTrue(result.isOk());
        assertEquals(-1L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("resolves DK reverse charge rate as 2500 basis points (self-assessed at standard rate)")
    void resolveReverseChargeRate() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.REVERSE_CHARGE, EFFECTIVE);

        assertTrue(result.isOk());
        assertEquals(2500L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("returns UnknownTaxCode error for unrecognised string code")
    void resolveUnknownStringCode_returnsError() {
        Result<Long> result = resolver.resolveVatRate(dkPlugin, "NONEXISTENT_CODE", EFFECTIVE);

        assertTrue(result.isErr());
        assertInstanceOf(VatRuleError.UnknownTaxCode.class,
                ((Result.Err<Long>) result).error());
        assertEquals("NONEXISTENT_CODE",
                ((VatRuleError.UnknownTaxCode) ((Result.Err<Long>) result).error()).code());
    }

    @Test
    @DisplayName("string overload resolves 'STANDARD' case-insensitively")
    void resolveViaStringOverload_caseInsensitive() {
        Result<Long> lower = resolver.resolveVatRate(dkPlugin, "standard", EFFECTIVE);
        Result<Long> upper = resolver.resolveVatRate(dkPlugin, "STANDARD", EFFECTIVE);

        assertTrue(lower.isOk());
        assertTrue(upper.isOk());
        assertEquals(((Result.Ok<Long>) upper).value(), ((Result.Ok<Long>) lower).value());
    }

    @Test
    @DisplayName("future effective date resolves rate (plugin has static schedule)")
    void resolveFutureEffectiveDate() {
        LocalDate future = LocalDate.of(2030, 6, 15);
        Result<Long> result = resolver.resolveVatRate(dkPlugin, TaxCode.STANDARD, future);

        // DK plugin has no date-based rate schedule yet — returns the same 2500
        assertTrue(result.isOk());
        assertEquals(2500L, ((Result.Ok<Long>) result).value());
    }

    @Test
    @DisplayName("map() chains rate into a VAT calculation")
    void resolveAndMapToVatAmount() {
        long baseOere = 1_000_000L; // 10,000 DKK

        Result<Long> vatResult = resolver
                .resolveVatRate(dkPlugin, TaxCode.STANDARD, EFFECTIVE)
                .map(rate -> baseOere * rate / 10_000L);

        assertTrue(vatResult.isOk());
        assertEquals(250_000L, ((Result.Ok<Long>) vatResult).value()); // 2,500 DKK
    }
}
