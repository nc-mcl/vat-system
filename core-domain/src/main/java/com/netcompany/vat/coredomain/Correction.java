package com.netcompany.vat.coredomain;

import java.util.UUID;

/**
 * Records the formal correction of a previously submitted {@link VatReturn}.
 *
 * <p>Corrections follow the immutability principle: the original return is never
 * modified. Instead, a new {@code VatReturn} is created and this record links
 * the two. The audit trail captures the full chain of events.
 *
 * @param id                 immutable system identifier
 * @param originalReturnId   the {@link VatReturn} being corrected
 * @param correctedReturnId  the new {@link VatReturn} with the corrected values
 * @param reason             mandatory free-text explanation of why the correction is needed
 */
public record Correction(
        UUID id,
        UUID originalReturnId,
        UUID correctedReturnId,
        String reason
) {}
