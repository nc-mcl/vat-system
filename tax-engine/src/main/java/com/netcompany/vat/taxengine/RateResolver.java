package com.netcompany.vat.taxengine;

import com.netcompany.vat.coredomain.Result;
import com.netcompany.vat.coredomain.TaxCode;
import com.netcompany.vat.coredomain.VatRuleError;
import com.netcompany.vat.coredomain.jurisdiction.JurisdictionPlugin;

import java.time.LocalDate;

/**
 * Resolves the VAT rate in basis points for a given tax code by delegating to the
 * jurisdiction plugin. Never hardcodes rates — all rates come from the plugin.
 *
 * <p>Rates are returned as basis points: 2500 = 25.00%, 0 = 0.00%, −1 = not applicable.
 * A rate of −1 is a valid outcome for EXEMPT and OUT_OF_SCOPE codes; it is NOT an error.
 * An error is returned only when the plugin does not recognise the tax code at all.
 *
 * @implNote Danish example — resolving the STANDARD rate for Denmark on 2026-01-01
 * returns {@code Result.ok(2500L)} (25.00%). Resolving EXEMPT returns {@code Result.ok(-1L)}.
 * Passing an unsupported code returns {@code Result.err(UnknownTaxCode("X"))}.
 */
public final class RateResolver {

    /**
     * Resolves the VAT rate for the given tax code as of the given date.
     *
     * <p>Delegates to {@link JurisdictionPlugin#getVatRateInBasisPoints(TaxCode, LocalDate)}.
     * Returns {@code Result.err(UnknownTaxCode)} if the plugin throws
     * {@code IllegalArgumentException} for an unrecognised code.
     *
     * @param plugin        the active jurisdiction plugin
     * @param taxCode       the VAT treatment code to look up
     * @param effectiveDate the date on which the rate must be valid
     * @return {@code Ok(rateInBasisPoints)} or {@code Err(UnknownTaxCode)}
     */
    public Result<Long> resolveVatRate(
            JurisdictionPlugin plugin,
            TaxCode taxCode,
            LocalDate effectiveDate) {
        try {
            long rate = plugin.getVatRateInBasisPoints(taxCode, effectiveDate);
            return Result.ok(rate);
        } catch (IllegalArgumentException e) {
            return Result.err(new VatRuleError.UnknownTaxCode(taxCode.name()));
        }
    }

    /**
     * Convenience overload that parses a tax code string before resolving.
     *
     * <p>Returns {@code Err(UnknownTaxCode)} if the string cannot be parsed as a
     * known {@link TaxCode} constant (case-insensitive).
     *
     * @param plugin        the active jurisdiction plugin
     * @param taxCodeStr    the tax code name (e.g. "STANDARD", "ZERO_RATED")
     * @param effectiveDate the date on which the rate must be valid
     * @return {@code Ok(rateInBasisPoints)} or {@code Err(UnknownTaxCode)}
     */
    public Result<Long> resolveVatRate(
            JurisdictionPlugin plugin,
            String taxCodeStr,
            LocalDate effectiveDate) {
        try {
            TaxCode taxCode = TaxCode.valueOf(taxCodeStr.toUpperCase());
            return resolveVatRate(plugin, taxCode, effectiveDate);
        } catch (IllegalArgumentException e) {
            return Result.err(new VatRuleError.UnknownTaxCode(taxCodeStr));
        }
    }
}
