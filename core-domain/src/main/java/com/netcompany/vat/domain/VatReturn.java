package com.netcompany.vat.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The top-level aggregate for a single VAT filing period in one jurisdiction.
 *
 * <p>Immutable once created. Corrections never mutate this record — they create
 * a new {@code VatReturn} linked via a {@link Correction}.
 *
 * <p>{@code netVat}, {@code resultType}, and {@code claimAmount} are derived at
 * construction time from {@code outputVat} and {@code inputVatDeductible}.
 * They are stored in the record for convenience and to avoid recomputation.
 *
 * @param id                   immutable system identifier
 * @param jurisdictionCode     the jurisdiction this return is filed in
 * @param periodId             the {@link TaxPeriod} this return covers
 * @param outputVat            total output VAT collected (sales VAT), in øre
 * @param inputVatDeductible   total deductible input VAT (purchase VAT), in øre
 * @param netVat               derived: outputVat − inputVatDeductible
 * @param resultType           derived: PAYABLE if netVat &gt; 0, CLAIMABLE if &lt; 0, ZERO otherwise
 * @param claimAmount          derived: absolute value of netVat when CLAIMABLE, else zero
 * @param status               current lifecycle status
 * @param jurisdictionFields   authority-specific fields (e.g. SKAT rubrik values for DK).
 *                             Keys and value formats are defined by the jurisdiction plugin.
 * @param assembledAt          timestamp when the return was assembled, or null
 * @param submittedAt          timestamp when the return was submitted to the authority, or null
 * @param acceptedAt           timestamp when the authority accepted the return, or null
 * @param skatReference        authority reference number assigned on acceptance, or null
 */
public record VatReturn(
        UUID id,
        JurisdictionCode jurisdictionCode,
        UUID periodId,
        MonetaryAmount outputVat,
        MonetaryAmount inputVatDeductible,
        MonetaryAmount netVat,
        ResultType resultType,
        MonetaryAmount claimAmount,
        VatReturnStatus status,
        Map<String, Object> jurisdictionFields,
        Instant assembledAt,
        Instant submittedAt,
        Instant acceptedAt,
        String skatReference
) {

    /**
     * Factory method that derives {@code netVat}, {@code resultType}, and {@code claimAmount}
     * automatically from {@code outputVat} and {@code inputVatDeductible}.
     * Timestamp fields default to {@code null}.
     */
    public static VatReturn of(
            UUID id,
            JurisdictionCode jurisdictionCode,
            UUID periodId,
            MonetaryAmount outputVat,
            MonetaryAmount inputVatDeductible,
            VatReturnStatus status,
            Map<String, Object> jurisdictionFields
    ) {
        return of(id, jurisdictionCode, periodId, outputVat, inputVatDeductible,
                status, jurisdictionFields, null, null, null, null);
    }

    /**
     * Full factory method that derives {@code netVat}, {@code resultType}, and {@code claimAmount}
     * automatically, and accepts nullable lifecycle timestamp fields.
     */
    public static VatReturn of(
            UUID id,
            JurisdictionCode jurisdictionCode,
            UUID periodId,
            MonetaryAmount outputVat,
            MonetaryAmount inputVatDeductible,
            VatReturnStatus status,
            Map<String, Object> jurisdictionFields,
            Instant assembledAt,
            Instant submittedAt,
            Instant acceptedAt,
            String skatReference
    ) {
        MonetaryAmount netVat = outputVat.subtract(inputVatDeductible);
        ResultType resultType;
        MonetaryAmount claimAmount;

        if (netVat.isPositive()) {
            resultType = ResultType.PAYABLE;
            claimAmount = MonetaryAmount.ZERO;
        } else if (netVat.isNegative()) {
            resultType = ResultType.CLAIMABLE;
            claimAmount = netVat.negate();
        } else {
            resultType = ResultType.ZERO;
            claimAmount = MonetaryAmount.ZERO;
        }

        return new VatReturn(
                id, jurisdictionCode, periodId,
                outputVat, inputVatDeductible,
                netVat, resultType, claimAmount,
                status, jurisdictionFields,
                assembledAt, submittedAt, acceptedAt, skatReference
        );
    }
}
