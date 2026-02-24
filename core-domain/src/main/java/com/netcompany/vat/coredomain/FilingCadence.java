package com.netcompany.vat.coredomain;

/**
 * Frequency at which a taxpayer must file VAT returns in a jurisdiction.
 *
 * <p>In Denmark (SKAT rules):
 * <ul>
 *   <li>{@link #MONTHLY} — annual turnover &gt; 50M DKK</li>
 *   <li>{@link #QUARTERLY} — annual turnover between 5M and 50M DKK (default)</li>
 *   <li>{@link #SEMI_ANNUAL} — annual turnover &lt; 5M DKK</li>
 *   <li>{@link #ANNUAL} — very small businesses or non-profit entities (authority discretion)</li>
 * </ul>
 *
 * <p>Note: {@code SEMI_ANNUAL} was identified as missing from an earlier design iteration
 * and added following Business Analyst validation against SKAT documentation.
 */
public enum FilingCadence {
    MONTHLY,
    QUARTERLY,
    SEMI_ANNUAL,
    ANNUAL
}
