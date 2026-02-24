package com.netcompany.vat.domain;

import java.util.UUID;

/**
 * Sealed hierarchy of domain-level VAT rule violations.
 *
 * <p>Use with {@link Result} to return typed errors from tax-engine operations
 * without throwing exceptions. Pattern-match exhaustively using Java's
 * {@code switch} expression:
 *
 * <pre>{@code
 * switch (error) {
 *     case VatRuleError.UnknownTaxCode e    -> handleUnknown(e.code());
 *     case VatRuleError.InvalidTransaction e -> handleInvalid(e.reason());
 *     case VatRuleError.MissingVatNumber e  -> handleMissing(e.counterpartyId());
 *     case VatRuleError.PeriodAlreadyFiled e -> handleFiled(e.periodId());
 * }
 * }</pre>
 */
public sealed interface VatRuleError
        permits VatRuleError.UnknownTaxCode,
                VatRuleError.InvalidTransaction,
                VatRuleError.MissingVatNumber,
                VatRuleError.PeriodAlreadyFiled {

    /** A tax code was referenced that the jurisdiction plugin does not recognise. */
    record UnknownTaxCode(String code) implements VatRuleError {}

    /** A transaction violated a VAT business rule (e.g. negative taxable amount on output). */
    record InvalidTransaction(String reason) implements VatRuleError {}

    /**
     * A counterparty that requires a VAT number (e.g. reverse-charge recipient)
     * does not have one recorded.
     */
    record MissingVatNumber(String counterpartyId) implements VatRuleError {}

    /**
     * An operation was attempted on a period that has already been filed.
     * All filed periods are immutable — use a correction instead.
     */
    record PeriodAlreadyFiled(UUID periodId) implements VatRuleError {}
}
