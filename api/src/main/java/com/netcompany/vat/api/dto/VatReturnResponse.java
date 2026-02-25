package com.netcompany.vat.api.dto;

/**
 * Response body for a VAT return.
 *
 * <p>All monetary values ({@code outputVat}, {@code inputVat}, {@code netVat},
 * {@code rubrikA}, {@code rubrikB}) are in <strong>øre</strong> (1 DKK = 100 øre).
 *
 * @param id               return UUID
 * @param periodId         UUID of the tax period this return covers
 * @param jurisdictionCode jurisdiction (e.g. "DK")
 * @param outputVat        total output VAT (sales VAT), in øre
 * @param inputVat         total deductible input VAT (purchase VAT), in øre
 * @param netVat           derived: outputVat − inputVat (positive = payable, negative = claimable)
 * @param resultType       PAYABLE / CLAIMABLE / ZERO
 * @param rubrikA          EU purchases total (rubrikA goods + services), in øre
 * @param rubrikB          EU sales total (rubrikB goods + services), in øre
 * @param status           lifecycle status (DRAFT / SUBMITTED / ACCEPTED / REJECTED)
 * @param assembledAt      ISO 8601 timestamp when the return was assembled, or null
 * @param submittedAt      ISO 8601 timestamp when submitted to SKAT, or null
 * @param acceptedAt       ISO 8601 timestamp when accepted by SKAT, or null
 * @param skatReference    SKAT reference number, or null if not yet submitted
 */
public record VatReturnResponse(
        String id,
        String periodId,
        String jurisdictionCode,
        long outputVat,
        long inputVat,
        long netVat,
        String resultType,
        long rubrikA,
        long rubrikB,
        String status,
        String assembledAt,
        String submittedAt,
        String acceptedAt,
        String skatReference
) {}
