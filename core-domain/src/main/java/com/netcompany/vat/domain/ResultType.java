package com.netcompany.vat.domain;

/**
 * Outcome of a VAT period: whether the taxpayer owes VAT, is entitled to a refund,
 * or has a net-zero position.
 *
 * <p>Derived from: {@code netVat = outputVat - inputVatDeductible}.
 * <ul>
 *   <li>{@link #PAYABLE} — netVat &gt; 0: taxpayer owes this amount to the authority</li>
 *   <li>{@link #CLAIMABLE} — netVat &lt; 0: taxpayer may reclaim the absolute value</li>
 *   <li>{@link #ZERO} — netVat == 0: no payment or refund due</li>
 * </ul>
 */
public enum ResultType {
    PAYABLE,
    CLAIMABLE,
    ZERO
}
