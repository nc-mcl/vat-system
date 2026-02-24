/**
 * @file calculator.ts
 * @description Core VAT arithmetic — pure bigint functions with no side effects.
 *
 * All monetary values are in the smallest currency unit (øre for DKK, eurocent for EUR).
 * All rates are in basis points: 2500n = 25.00%, 10000n = 100%.
 *
 * Rounding rule: integer division always truncates toward zero (floor for positive values).
 * This is the standard bigint `/` behaviour in JavaScript/TypeScript — no rounding library needed.
 * Rounding is documented explicitly per function.
 */

import type { BasisPoints, Money } from '../../core-domain/src/types.js';

/**
 * Calculate output VAT from a net (ex-VAT) taxable amount.
 *
 * Formula: floor(amountExclVat × rateInBasisPoints / 10000)
 * Rounding: truncates toward zero (down for positive amounts).
 *
 * @example
 * // 100 DKK (10 000 øre) × 25% = 25 DKK (2 500 øre)
 * calculateOutputVat(10_000n, 2500n) // => 2500n
 *
 * @example
 * // Fractional result rounds down: 333 øre × 25% = 83.25 → 83 øre
 * calculateOutputVat(333n, 2500n) // => 83n
 *
 * @example
 * // Zero-rated: any amount × 0% = 0
 * calculateOutputVat(10_000n, 0n) // => 0n
 */
export function calculateOutputVat(amountExclVat: Money, rateInBasisPoints: BasisPoints): Money {
  // basis points / 10 000 = rate fraction. E.g. 2500 / 10000 = 0.25
  return (amountExclVat * rateInBasisPoints) / 10_000n;
}

/**
 * Calculate the deductible portion of input VAT.
 *
 * For fully taxable businesses, deductionRate = 10000n (100%).
 * For mixed-use businesses, a partial rate is applied (pro-rata).
 * For exempt supplies, deductionRate = 0n (no recovery).
 *
 * Formula: floor(inputVat × deductionRate / 10000)
 * Rounding: truncates toward zero.
 *
 * @example
 * // Fully deductible: 2500 øre input VAT, 100% deduction rate
 * calculateInputVatDeduction(2500n, 10_000n) // => 2500n
 *
 * @example
 * // Partial deduction: 2500 øre input VAT, 50% deduction rate (mixed use)
 * calculateInputVatDeduction(2500n, 5_000n) // => 1250n
 *
 * @example
 * // No deduction: exempt business
 * calculateInputVatDeduction(2500n, 0n) // => 0n
 */
export function calculateInputVatDeduction(inputVat: Money, deductionRate: BasisPoints): Money {
  return (inputVat * deductionRate) / 10_000n;
}

/**
 * Calculate the net VAT position for a filing period.
 *
 * Net VAT = output VAT − deductible input VAT.
 * A positive result means VAT is owed to the authority (payable).
 * A negative result means a refund is claimable from the authority.
 *
 * @example
 * // Normal payable: output 5000 øre, input 2000 øre → owe 3000 øre
 * calculateNetVat(5_000n, 2_000n) // => 3000n
 *
 * @example
 * // Claimable refund: large purchases, small sales
 * calculateNetVat(1_000n, 4_000n) // => -3000n
 *
 * @example
 * // Zero: balanced period
 * calculateNetVat(2_500n, 2_500n) // => 0n
 */
export function calculateNetVat(outputVat: Money, deductibleInputVat: Money): Money {
  return outputVat - deductibleInputVat;
}

/**
 * Determine whether the net VAT result is payable, claimable, or zero.
 *
 * - 'payable'   — net > 0: taxpayer owes VAT to the authority
 * - 'claimable' — net < 0: taxpayer has a refund entitlement
 * - 'zero'      — net = 0: no VAT movement (e.g. nil period or perfectly balanced)
 *
 * @example
 * determineResultType(3_000n)  // => 'payable'
 * determineResultType(-1_000n) // => 'claimable'
 * determineResultType(0n)      // => 'zero'
 */
export function determineResultType(netVat: Money): 'payable' | 'claimable' | 'zero' {
  if (netVat > 0n) return 'payable';
  if (netVat < 0n) return 'claimable';
  return 'zero';
}
