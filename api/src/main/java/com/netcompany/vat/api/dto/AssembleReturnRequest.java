package com.netcompany.vat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for assembling a VAT return for a period.
 *
 * @param periodId         UUID of the open tax period
 * @param jurisdictionCode jurisdiction to file in (e.g. "DK")
 */
public record AssembleReturnRequest(
        @NotBlank String periodId,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String jurisdictionCode
) {}
