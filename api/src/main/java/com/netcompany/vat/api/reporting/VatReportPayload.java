package com.netcompany.vat.api.reporting;

import java.time.Instant;
import java.util.UUID;

/**
 * A generated report payload produced from a persisted VAT return.
 *
 * <p>The {@code momsangivelse} field contains the formatted Danish VAT return data.
 * The {@code generatedAt} timestamp records when this payload was produced.
 *
 * @param returnId        the VAT return this payload was generated from
 * @param generatedAt     when this payload was generated (UTC)
 * @param format          report format identifier (e.g. "DK_MOMSANGIVELSE_JSON")
 * @param momsangivelse   the formatted DK momsangivelse; non-null for DK returns
 */
public record VatReportPayload(
        UUID returnId,
        Instant generatedAt,
        String format,
        DkMomsangivelse momsangivelse
) {}
