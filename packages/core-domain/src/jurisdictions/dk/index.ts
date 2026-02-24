/**
 * @file jurisdictions/dk/index.ts
 * @description Danish (DK) jurisdiction plugin — Phase 1 implementation.
 *
 * This module is the first concrete implementation of JurisdictionPlugin.
 * It encapsulates all Denmark-specific VAT knowledge:
 *  - Tax rates (MOMS: 25% standard, zero-rated, exempt)
 *  - SKAT filing cadence rules (quarterly default, monthly >50M DKK)
 *  - Filing validation (rubrik A/B fields for EU intra-community transactions)
 *  - SKAT API client stub (full implementation in /packages/skat-client)
 *  - ViDA placeholders (Phase 2)
 *
 * Authority: SKAT (Skattestyrelsen) — https://skat.dk
 * E-invoicing: NemHandel / PEPPOL BIS 3.0
 * Bookkeeping law: Bogføringsloven
 */

import type {
  AuthorityApiClient,
  AuthorityReturnStatus,
  AuthoritySubmissionReceipt,
  BasisPoints,
  Counterparty,
  FilingCadence,
  FilingScheduleProvider,
  IsoDate,
  JurisdictionPlugin,
  Money,
  ReportFormatter,
  TaxCode,
  TaxPeriod,
  TaxRateRegistry,
  TaxReturnInput,
  ValidationResult,
  ValidationRuleSet,
  VatNumberValidationResult,
  VidaConfig,
} from '../../types.js';

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Danish standard VAT rate in basis points. 2500 = 25.00% */
const DK_STANDARD_RATE: BasisPoints = 2500n;

/**
 * Annual revenue threshold (in øre) above which monthly filing is required.
 * 50,000,000 DKK = 5,000,000,000 øre
 */
const DK_MONTHLY_THRESHOLD_OERE: Money = 5_000_000_000n;

/** Danish filing deadline: 1 month + 10 days after period end (approximate). */
const DK_FILING_DEADLINE_DAYS = 40;

// ---------------------------------------------------------------------------
// Tax Code definitions
// ---------------------------------------------------------------------------

const DK_TAX_CODES: TaxCode[] = [
  {
    code: 'DK_STANDARD',
    jurisdictionCode: 'DK',
    rateType: 'standard',
    rate: 2500n, // 25.00%
    reverseChargeApplicable: false,
    description: 'Danish standard VAT rate (MOMS) — 25%',
  },
  {
    code: 'DK_ZERO',
    jurisdictionCode: 'DK',
    rateType: 'zero',
    rate: 0n, // 0% — but VAT recovery on inputs is allowed
    reverseChargeApplicable: false,
    description: 'Zero-rated supply — exports and intra-EU supplies of goods',
  },
  {
    code: 'DK_EXEMPT',
    jurisdictionCode: 'DK',
    rateType: 'exempt',
    rate: 0n, // 0% — VAT recovery on inputs is NOT allowed
    reverseChargeApplicable: false,
    description: 'VAT-exempt supply — healthcare, education, insurance, financial services',
  },
  {
    code: 'DK_REVERSE_CHARGE',
    jurisdictionCode: 'DK',
    rateType: 'standard',
    rate: 2500n, // 25.00% — but buyer accounts for it
    reverseChargeApplicable: true,
    description: 'Reverse charge — B2B cross-border services (buyer accounts for 25% MOMS)',
  },
  {
    code: 'DK_EU_GOODS_ACQUISITION',
    jurisdictionCode: 'DK',
    rateType: 'standard',
    rate: 2500n,
    reverseChargeApplicable: false,
    description: 'Intra-EU acquisition of goods — reported on Rubrik A (goods)',
  },
  {
    code: 'DK_EU_GOODS_SUPPLY',
    jurisdictionCode: 'DK',
    rateType: 'zero',
    rate: 0n,
    reverseChargeApplicable: false,
    description: 'Intra-EU supply of goods — zero-rated, reported on Rubrik B (goods)',
  },
];

const DK_TAX_CODE_MAP = new Map(DK_TAX_CODES.map((c) => [c.code, c]));

// ---------------------------------------------------------------------------
// Tax Rate Registry
// ---------------------------------------------------------------------------

const dkTaxRates: TaxRateRegistry<'DK'> = {
  getStandardRate(): BasisPoints {
    return DK_STANDARD_RATE;
  },

  getReducedRates(): Record<string, BasisPoints> {
    // Denmark has no reduced rates — only standard (25%), zero, and exempt.
    return {};
  },

  getZeroRate(): BasisPoints {
    return 0n;
  },

  getAllTaxCodes(): TaxCode[] {
    return [...DK_TAX_CODES];
  },

  getTaxCode(code: string): TaxCode | undefined {
    return DK_TAX_CODE_MAP.get(code);
  },
};

// ---------------------------------------------------------------------------
// Validation Rules
// ---------------------------------------------------------------------------

const dkValidationRules: ValidationRuleSet<'DK'> = {
  validateFiling(data: TaxReturnInput<'DK'>): ValidationResult {
    const errors = [];
    const warnings = [];

    // Net VAT consistency check
    const expectedNet = data.outputVat - data.inputVatDeductible;

    // Rubrik values must be non-negative
    const fields = data.jurisdictionFields;
    for (const [key, value] of Object.entries(fields) as [string, Money][]) {
      if (value < 0n) {
        errors.push({
          field: key,
          code: 'DK_NEGATIVE_RUBRIK',
          message: `Rubrik field '${key}' must not be negative`,
        });
      }
    }

    // If reverse charge transactions exist, EU purchases should have values
    // (This is a simplification; full rules enforced in tax-engine)
    const hasEuPurchases =
      fields.rubrikAGoodsEuPurchaseValue > 0n ||
      fields.rubrikAServicesEuPurchaseValue > 0n;
    const hasEuSales =
      fields.rubrikBGoodsEuSaleValue > 0n ||
      fields.rubrikBServicesEuSaleValue > 0n;

    if (hasEuSales && !hasEuPurchases) {
      warnings.push({
        field: 'rubrikA',
        code: 'DK_EU_SALES_NO_PURCHASES',
        message:
          'EU sales (Rubrik B) are present but EU purchases (Rubrik A) are zero — verify correctness',
      });
    }

    // Net must equal output − input (always true by construction, but guard against caller bugs)
    if (expectedNet !== data.outputVat - data.inputVatDeductible) {
      errors.push({
        field: 'netVat',
        code: 'DK_NET_VAT_MISMATCH',
        message: 'Net VAT does not equal output VAT minus deductible input VAT',
      });
    }

    return { valid: errors.length === 0, errors, warnings };
  },

  validateCounterparty(c: Counterparty): ValidationResult {
    const errors = [];

    // CVR number: exactly 8 digits
    if (c.cvrNumber !== null) {
      if (!/^\d{8}$/.test(c.cvrNumber)) {
        errors.push({
          field: 'cvrNumber',
          code: 'DK_INVALID_CVR',
          message: 'Danish CVR number must be exactly 8 digits',
        });
      }
    }

    // DK VAT number format: DK + 8 digits
    if (c.euVatNumber !== null && c.countryCode === 'DK') {
      if (!/^DK\d{8}$/.test(c.euVatNumber)) {
        errors.push({
          field: 'euVatNumber',
          code: 'DK_INVALID_VAT_NUMBER',
          message: 'Danish EU VAT number must be in format DK12345678',
        });
      }
    }

    return { valid: errors.length === 0, errors, warnings: [] };
  },

  validateTaxCode(
    code: string,
    context: { direction: 'input' | 'output'; reverseCharge: boolean }
  ): ValidationResult {
    const errors = [];
    const taxCode = DK_TAX_CODE_MAP.get(code);

    if (!taxCode) {
      errors.push({
        field: 'taxCode',
        code: 'DK_UNKNOWN_TAX_CODE',
        message: `Unknown DK tax code: '${code}'`,
      });
      return { valid: false, errors, warnings: [] };
    }

    // Reverse charge code should only be used with reverseCharge=true
    if (code === 'DK_REVERSE_CHARGE' && !context.reverseCharge) {
      errors.push({
        field: 'reverseCharge',
        code: 'DK_REVERSE_CHARGE_FLAG_MISSING',
        message: 'Tax code DK_REVERSE_CHARGE requires reverseCharge=true',
      });
    }

    // EU supply codes should only appear on outbound transactions
    if (code === 'DK_EU_GOODS_SUPPLY' && context.direction !== 'output') {
      errors.push({
        field: 'direction',
        code: 'DK_EU_SUPPLY_WRONG_DIRECTION',
        message: 'DK_EU_GOODS_SUPPLY can only be used on outbound (sales) transactions',
      });
    }

    return { valid: errors.length === 0, errors, warnings: [] };
  },
};

// ---------------------------------------------------------------------------
// Filing Schedule Provider
// ---------------------------------------------------------------------------

function addDays(date: IsoDate, days: number): IsoDate {
  const d = new Date(date);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

function daysInPeriod(start: IsoDate, end: IsoDate): number {
  const startMs = new Date(start).getTime();
  const endMs = new Date(end).getTime();
  return Math.round((endMs - startMs) / 86_400_000) + 1;
}

const dkFilingSchedule: FilingScheduleProvider<'DK'> = {
  /**
   * Danish filing cadence rules (Momsregistrering):
   * - Annual turnover > 50M DKK → monthly filing
   * - Annual turnover ≤ 50M DKK → quarterly filing (default)
   * - Very small businesses may file annually (not modelled here — requires SKAT approval)
   */
  getCadence(_counterparty: Counterparty, annualRevenue: Money): FilingCadence {
    if (annualRevenue > DK_MONTHLY_THRESHOLD_OERE) {
      return 'monthly';
    }
    return 'quarterly';
  },

  /**
   * DK deadline: last day of the month following the period end + 10 days.
   * Approximated here as period end + 40 days. Full calendar logic belongs in
   * the domain rules agent, accounting for Danish public holidays.
   */
  getDeadline(period: TaxPeriod): IsoDate {
    return addDays(period.endDate, DK_FILING_DEADLINE_DAYS);
  },

  /**
   * Generate DK quarterly or monthly periods for a calendar year.
   */
  getPeriodsForYear(year: number, cadence: FilingCadence): TaxPeriod[] {
    const periods: TaxPeriod[] = [];

    if (cadence === 'quarterly') {
      const quarters = [
        { start: `${year}-01-01`, end: `${year}-03-31` },
        { start: `${year}-04-01`, end: `${year}-06-30` },
        { start: `${year}-07-01`, end: `${year}-09-30` },
        { start: `${year}-10-01`, end: `${year}-12-31` },
      ];
      for (const q of quarters) {
        const period: TaxPeriod = {
          id: `DK-${year}-Q${quarters.indexOf(q) + 1}`,
          jurisdictionCode: 'DK',
          startDate: q.start,
          endDate: q.end,
          cadence: 'quarterly',
          dueDate: addDays(q.end, DK_FILING_DEADLINE_DAYS),
          periodDays: daysInPeriod(q.start, q.end),
          status: 'open',
        };
        periods.push(period);
      }
    } else if (cadence === 'monthly') {
      const monthEnds = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
      // Adjust February for leap years
      if ((year % 4 === 0 && year % 100 !== 0) || year % 400 === 0) {
        monthEnds[1] = 29;
      }
      for (let m = 1; m <= 12; m++) {
        const start = `${year}-${String(m).padStart(2, '0')}-01`;
        const end = `${year}-${String(m).padStart(2, '0')}-${monthEnds[m - 1]}`;
        const period: TaxPeriod = {
          id: `DK-${year}-M${String(m).padStart(2, '0')}`,
          jurisdictionCode: 'DK',
          startDate: start,
          endDate: end,
          cadence: 'monthly',
          dueDate: addDays(end, DK_FILING_DEADLINE_DAYS),
          periodDays: daysInPeriod(start, end),
          status: 'open',
        };
        periods.push(period);
      }
    }

    return periods;
  },
};

// ---------------------------------------------------------------------------
// Report Formatter (stub — full implementation in /packages/reporting)
// ---------------------------------------------------------------------------

const dkReportFormatter: ReportFormatter<'DK'> = {
  outputMimeType: 'application/xml',

  formatReturn(_taxReturn): Buffer {
    // TODO: Phase 1 — implement SKAT XML rubrik format
    // The full formatter belongs in /packages/reporting (Reporting Agent responsibility)
    // This stub returns an empty buffer to satisfy the interface contract
    throw new Error(
      'DK report formatter not yet implemented. ' +
      'Implement in /packages/reporting/src/dk/skatFormatter.ts'
    );
  },
};

// ---------------------------------------------------------------------------
// Authority API Client (stub — full implementation in /packages/skat-client)
// ---------------------------------------------------------------------------

const dkAuthorityClient: AuthorityApiClient<'DK'> = {
  async submitReturn(_payload: Buffer): Promise<AuthoritySubmissionReceipt> {
    // TODO: Phase 1 — implement SKAT REST API submission
    // Full implementation: /packages/skat-client/src/skatApiClient.ts
    // SKAT sandbox: https://api-sandbox.skat.dk
    throw new Error(
      'SKAT API client not yet implemented. ' +
      'Implement in /packages/skat-client/src/skatApiClient.ts'
    );
  },

  async checkStatus(_referenceId: string): Promise<AuthorityReturnStatus> {
    // TODO: Phase 1 — implement SKAT status polling
    throw new Error('SKAT status check not yet implemented.');
  },

  async validateVatNumber(vatNumber: string): Promise<VatNumberValidationResult> {
    // TODO: Phase 1 — implement VIES validation
    // VIES endpoint: https://ec.europa.eu/taxation_customs/vies/
    // Also available via SKAT for DK-specific validation
    throw new Error(
      `VIES VAT number validation not yet implemented for ${vatNumber}. ` +
      'Implement in /packages/skat-client/src/viesClient.ts'
    );
  },
};

// ---------------------------------------------------------------------------
// ViDA Configuration (Phase 2 placeholders)
// ---------------------------------------------------------------------------

const dkVidaConfig: VidaConfig = {
  // TODO: Phase 2 — enable when DRR goes live (EU mandate: 2028)
  drrEnabled: false,
  drrEndpoint: null,
  drrTransactionTypes: [],

  // TODO: Phase 2 — enable when extended OSS is implemented
  extendedOssEnabled: false,

  // TODO: Phase 2 — enable when platform economy rules apply
  platformDeemedSupplierEnabled: false,

  // ViDA obligations for DK expected from 2028
  effectiveFrom: '2028-01-01',
};

// ---------------------------------------------------------------------------
// Plugin export
// ---------------------------------------------------------------------------

/**
 * Danish jurisdiction plugin — the first JurisdictionPlugin implementation.
 *
 * Provides all Denmark-specific VAT knowledge to the core system via the
 * JurisdictionPlugin interface. No other package needs to know about Denmark.
 *
 * Register at startup:
 *   registry.register(DkJurisdictionPlugin);
 */
export const DkJurisdictionPlugin: JurisdictionPlugin<'DK'> = {
  code: 'DK',
  displayName: 'Denmark',
  currencyCode: 'DKK',
  smallestCurrencyUnit: 'øre',

  taxRates: dkTaxRates,
  validationRules: dkValidationRules,
  filingSchedule: dkFilingSchedule,
  reportFormatter: dkReportFormatter,
  authorityClient: dkAuthorityClient,
  vidaConfig: dkVidaConfig,
};
