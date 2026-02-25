package com.netcompany.vat.skatclient.dto;

import java.math.BigDecimal;

/**
 * SKAT wire-format payload for a VAT return submission (Momsangivelse).
 *
 * <p>All monetary values are in <strong>DKK</strong> (converted from øre at the
 * anti-corruption boundary). The SKAT API expects decimal DKK, not integer øre.
 *
 * <p>This record mirrors the SKAT Momsangivelse rubrik structure. Field names match
 * SKAT's published API schema — do not rename without updating the Phase 2 mapping.
 *
 * <p><strong>Note:</strong> No OIOUBL 2.1 fields are present. OIOUBL 2.1 is phased
 * out as of May 15, 2026. This format targets the SKAT REST API only.
 *
 * @param returnId              system return UUID (for correlation)
 * @param periodId              system period UUID (for correlation)
 * @param jurisdictionCode      e.g. "DK"
 * @param outputVatDkk          total output VAT (salgsmoms), in DKK
 * @param inputVatDkk           total deductible input VAT (købsmoms), in DKK
 * @param netVatDkk             net VAT (outputVat − inputVat), in DKK
 * @param rubrikAGoodsDkk       EU goods purchases (Rubrik A — varer), in DKK
 * @param rubrikAServicesDkk    EU/non-EU service purchases, reverse charge (Rubrik A — ydelser), in DKK
 * @param rubrikBGoodsDkk       EU goods sales, zero-rated (Rubrik B — varer), in DKK
 * @param rubrikBServicesDkk    EU service sales, reverse charge at buyer (Rubrik B — ydelser), in DKK
 * @param rubrikCOtherDkk       other exempt/zero-rated supplies (Rubrik C), in DKK
 * @param resultType            PAYABLE / CLAIMABLE / ZERO
 * @param status                DRAFT / SUBMITTED (always SUBMITTED when sent to SKAT)
 */
public record SkatSubmissionRequest(
        String returnId,
        String periodId,
        String jurisdictionCode,
        BigDecimal outputVatDkk,
        BigDecimal inputVatDkk,
        BigDecimal netVatDkk,
        BigDecimal rubrikAGoodsDkk,
        BigDecimal rubrikAServicesDkk,
        BigDecimal rubrikBGoodsDkk,
        BigDecimal rubrikBServicesDkk,
        BigDecimal rubrikCOtherDkk,
        String resultType,
        String status
) {}
