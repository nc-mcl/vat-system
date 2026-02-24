/**
 * @file result.ts
 * @description Result<T, E> monad for pure error handling in the tax engine.
 *
 * Business logic must never throw exceptions. Return Result<T, E> instead.
 * This enables callers to handle errors without try/catch and keeps functions pure.
 *
 * @example
 * // Rate lookup returns Ok on success or Err on unknown code
 * const result = resolveVatRate(plugin.taxRates, 'DK_STANDARD', '2026-01-01');
 * if (isOk(result)) {
 *   const rate = result.value; // 2500n — 25%
 * } else {
 *   console.error(result.error.message);
 * }
 */

/** Successful result containing a value. */
export type Ok<T> = { readonly ok: true; readonly value: T };

/** Failed result containing an error. */
export type Err<E> = { readonly ok: false; readonly error: E };

/** A discriminated union of Ok<T> or Err<E>. */
export type Result<T, E> = Ok<T> | Err<E>;

/**
 * Construct a successful Result wrapping a value.
 *
 * @example
 * ok(2500n)          // Ok<bigint>
 * ok({ rate: 2500n }) // Ok<{ rate: bigint }>
 */
export function ok<T>(value: T): Ok<T> {
  return { ok: true, value };
}

/**
 * Construct a failed Result wrapping an error.
 *
 * @example
 * err({ code: 'UNKNOWN_TAX_CODE', message: "Unknown code: 'DK_FOO'" })
 */
export function err<E>(error: E): Err<E> {
  return { ok: false, error };
}

/**
 * Type guard — narrows Result<T,E> to Ok<T>.
 *
 * @example
 * if (isOk(result)) {
 *   use(result.value); // TypeScript knows result.value is T
 * }
 */
export function isOk<T, E>(result: Result<T, E>): result is Ok<T> {
  return result.ok === true;
}

/**
 * Type guard — narrows Result<T,E> to Err<E>.
 *
 * @example
 * if (isErr(result)) {
 *   log(result.error.code); // TypeScript knows result.error is E
 * }
 */
export function isErr<T, E>(result: Result<T, E>): result is Err<E> {
  return result.ok === false;
}

/**
 * Transform the value inside an Ok result, leaving Err unchanged.
 * Equivalent to Functor.map — does not unwrap, just transforms.
 *
 * @example
 * // Convert rate from basis points to a display string
 * mapResult(ok(2500n), (bp) => `${bp / 100n}%`)  // => Ok('25%')
 * mapResult(err({ code: 'E' }), (bp) => `${bp}%`) // => Err unchanged
 */
export function mapResult<T, U, E>(
  result: Result<T, E>,
  fn: (value: T) => U,
): Result<U, E> {
  if (isOk(result)) {
    return ok(fn(result.value));
  }
  return result;
}
