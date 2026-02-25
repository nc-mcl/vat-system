package com.netcompany.vat.skatclient.mapper;

import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.skatclient.dto.SkatSubmissionRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Maps domain types to SKAT wire-format DTOs.
 *
 * <h2>Monetary conversion</h2>
 * <p>All internal monetary values are in <strong>øre</strong> (smallest DKK unit, long).
 * SKAT's API expects <strong>DKK</strong> as {@link BigDecimal} with two decimal places.
 * Conversion: {@code dkk = BigDecimal.valueOf(oere).divide(100, 2, HALF_UP)}.
 *
 * <p>Conversion happens <em>only</em> at this boundary. No floating-point arithmetic is used.
 *
 * <h2>Phase 2 notes</h2>
 * <p>This mapper documents all field mappings for the real SKAT REST API.
 * When Phase 2 begins, verify field names against the SKAT Momsangivelse API schema
 * at {@code https://api-sandbox.skat.dk}.
 */
public final class SkatMapper {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SkatMapper() {}

    /**
     * Maps a {@link VatReturn} to a {@link SkatSubmissionRequest} (SKAT wire format).
     *
     * <p>All monetary values are converted from øre to DKK at this boundary.
     * No business logic — pure structural mapping.
     *
     * @param vatReturn the fully assembled VAT return (must not be null)
     * @return SKAT wire-format submission payload ready for the authority API
     */
    public static SkatSubmissionRequest toSubmissionRequest(VatReturn vatReturn) {
        Map<String, Object> fields = vatReturn.jurisdictionFields();

        return new SkatSubmissionRequest(
                vatReturn.id().toString(),
                vatReturn.periodId().toString(),
                vatReturn.jurisdictionCode().name(),
                oereToDkk(vatReturn.outputVat().oere()),
                oereToDkk(vatReturn.inputVatDeductible().oere()),
                oereToDkk(vatReturn.netVat().oere()),
                oereToDkk(getLong(fields, "rubrikAGoodsEuPurchaseValue")),
                oereToDkk(getLong(fields, "rubrikAServicesEuPurchaseValue")),
                oereToDkk(getLong(fields, "rubrikBGoodsEuSaleValue")),
                oereToDkk(getLong(fields, "rubrikBServicesEuSaleValue")),
                oereToDkk(getLong(fields, "rubrikCOtherVatExemptSuppliesValue")),
                vatReturn.resultType().name(),
                vatReturn.status().name()
        );
    }

    /**
     * Converts an øre (long) value to DKK (BigDecimal, 2 decimal places).
     *
     * <p>Example: 25000 øre → 250.00 DKK
     */
    public static BigDecimal oereToDkk(long oere) {
        return BigDecimal.valueOf(oere).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private static long getLong(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
