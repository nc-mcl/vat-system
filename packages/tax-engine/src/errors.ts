/**
 * @file errors.ts
 * @description Shared error types for the VAT tax engine.
 *
 * All business logic errors are represented as values (via Result<T,E>),
 * never as thrown exceptions. Error codes are prefixed for easy identification.
 */

/**
 * A structured error from VAT rule evaluation.
 * Returned inside Err<VatRuleError> — never thrown.
 */
export interface VatRuleError {
  /** Machine-readable error code (e.g. 'UNKNOWN_TAX_CODE') */
  readonly code: string;
  /** Human-readable description of the error */
  readonly message: string;
  /** Optional: the field name that caused the error */
  readonly field?: string;
}
