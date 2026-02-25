package com.netcompany.vat.api.dto;

/**
 * Response body for a VAT transaction.
 *
 * <p>All monetary amounts ({@code amountExclVat}, {@code vatAmount}) are in
 * <strong>øre</strong> (1 DKK = 100 øre).
 *
 * @param id              transaction UUID
 * @param periodId        UUID of the owning tax period
 * @param taxCode         VAT treatment code (e.g. "STANDARD")
 * @param taxClassification full classification description
 * @param amountExclVat   base amount excluding VAT, in øre
 * @param vatAmount       computed VAT amount, in øre
 * @param rateBasisPoints VAT rate in basis points (e.g. 2500 = 25.00%)
 * @param transactionDate ISO 8601 tax point date
 * @param description     human-readable description
 * @param isReverseCharge true if the buyer accounts for VAT (self-assessment)
 * @param counterpartyId  counterparty UUID, or null if not set
 */
public record TransactionResponse(
        String id,
        String periodId,
        String taxCode,
        String taxClassification,
        long amountExclVat,
        long vatAmount,
        long rateBasisPoints,
        String transactionDate,
        String description,
        boolean isReverseCharge,
        String counterpartyId
) {}
