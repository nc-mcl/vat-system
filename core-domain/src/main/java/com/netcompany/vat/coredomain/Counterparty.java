package com.netcompany.vat.coredomain;

import java.util.UUID;

/**
 * A legal entity involved in VAT transactions — either the registered taxpayer,
 * a customer, or a supplier.
 *
 * <p>VAT number format is validated per jurisdiction by the jurisdiction plugin.
 * The core domain stores it as a raw string; formatting rules are jurisdiction-specific.
 *
 * @param id               immutable system identifier
 * @param jurisdictionCode the jurisdiction in which this counterparty is registered
 * @param vatNumber        the local VAT registration number (format varies by jurisdiction)
 * @param name             legal name of the entity
 */
public record Counterparty(
        UUID id,
        JurisdictionCode jurisdictionCode,
        String vatNumber,
        String name
) {}
