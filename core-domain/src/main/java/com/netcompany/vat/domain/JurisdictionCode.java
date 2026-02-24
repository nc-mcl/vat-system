package com.netcompany.vat.domain;

/**
 * Identifies a tax jurisdiction. This is the deliberate, minimal coupling point
 * when adding new jurisdictions — only this enum and a new plugin implementation
 * need to change; zero changes to core logic.
 */
public enum JurisdictionCode {

    /** Denmark — MOMS, administered by SKAT (Skattestyrelsen). */
    DK;

    /**
     * Resolves a jurisdiction code from a string representation (case-insensitive).
     *
     * @throws IllegalArgumentException if the code is unknown
     */
    public static JurisdictionCode fromString(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Jurisdiction code must not be blank");
        }
        return valueOf(code.trim().toUpperCase());
    }
}
