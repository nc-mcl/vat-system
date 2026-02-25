package com.netcompany.vat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a new VAT transaction.
 *
 * <p><strong>Monetary amounts are in øre (1 DKK = 100 øre).</strong>
 * For example, 10,000 DKK = 1,000,000 øre.
 *
 * @param periodId        UUID of the open tax period
 * @param taxCode         VAT treatment — one of: STANDARD, ZERO_RATED, EXEMPT, REVERSE_CHARGE, OUT_OF_SCOPE
 * @param amountExclVat   base taxable amount excluding VAT, in <strong>øre</strong>
 * @param transactionDate the tax point date (ISO 8601 date, e.g. "2026-01-15")
 * @param description     human-readable description of the transaction
 * @param counterpartyId  optional UUID of the counterparty (supplier or customer)
 */
public record CreateTransactionRequest(
        @NotBlank String periodId,
        @NotBlank String taxCode,
        @Positive long amountExclVat,
        @NotBlank String transactionDate,
        @Size(max = 500) String description,
        String counterpartyId
) {}
