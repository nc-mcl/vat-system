/**
 * @file rate-resolver.ts
 * @description VAT rate resolution from a jurisdiction's TaxRateRegistry.
 *
 * Rates are always sourced from the plugin — never hardcoded in this file.
 * The effectiveDate parameter is reserved for future rate versioning (e.g. when
 * a country changes its standard VAT rate on a specific date). Currently, the
 * registry holds one rate per tax code; versioning is a Phase 3 enhancement.
 */

import type { BasisPoints, IsoDate, JurisdictionCode, TaxRateRegistry } from '../../core-domain/src/types.js';
import { err, ok, type Result } from './result.js';
import type { VatRuleError } from './errors.js';

/**
 * Resolve the VAT rate in basis points for a tax code as of a given date.
 *
 * Looks up the tax code in the plugin's TaxRateRegistry. Returns an Err if the
 * code is unknown — callers must handle this case explicitly.
 *
 * @param taxRates - The TaxRateRegistry from the jurisdiction plugin
 * @param taxCode  - The tax code string (e.g. 'DK_STANDARD', 'DK_EXEMPT')
 * @param effectiveDate - The date for which the rate is needed (ISO 8601).
 *   Reserved for future rate versioning. Currently ignored in lookup.
 *
 * @returns Ok(rate in basis points) or Err with UNKNOWN_TAX_CODE
 *
 * @example
 * // Danish standard MOMS rate
 * resolveVatRate(dkPlugin.taxRates, 'DK_STANDARD', '2026-01-01')
 * // => Ok(2500n) — 25.00%
 *
 * @example
 * // Zero-rated export
 * resolveVatRate(dkPlugin.taxRates, 'DK_ZERO', '2026-01-01')
 * // => Ok(0n)
 *
 * @example
 * // Unknown code returns a structured error
 * resolveVatRate(dkPlugin.taxRates, 'DK_NONEXISTENT', '2026-01-01')
 * // => Err({ code: 'UNKNOWN_TAX_CODE', message: "Unknown tax code: 'DK_NONEXISTENT'" })
 */
export function resolveVatRate<J extends JurisdictionCode>(
  taxRates: TaxRateRegistry<J>,
  taxCode: string,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _effectiveDate: IsoDate,
): Result<BasisPoints, VatRuleError> {
  const code = taxRates.getTaxCode(taxCode);
  if (!code) {
    return err({
      code: 'UNKNOWN_TAX_CODE',
      message: `Unknown tax code: '${taxCode}'`,
      field: 'taxCode',
    });
  }
  return ok(code.rate);
}
