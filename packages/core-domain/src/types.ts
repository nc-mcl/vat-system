/**
 * @file types.ts
 * @description Core domain types for the multi-jurisdiction VAT system.
 *
 * Design principles:
 * - All entities are jurisdiction-agnostic at their core; jurisdiction-specific
 *   fields are expressed via generics keyed on JurisdictionCode.
 * - All monetary values are `bigint` representing the smallest currency unit
 *   (e.g. øre for DKK, eurocents for EUR). Never use `number` for money.
 * - All dates are `string` in ISO 8601 format (UTC).
 * - Adding a new jurisdiction requires: (1) adding its code to JurisdictionCode,
 *   (2) adding its fields to JurisdictionReturnFields, and (3) implementing
 *   JurisdictionPlugin. Zero changes to any other type in this file.
 */

// ---------------------------------------------------------------------------
// Primitive aliases
// ---------------------------------------------------------------------------

/** Monetary value in the smallest currency unit (øre, eurocent, etc.). Never a float. */
export type Money = bigint;

/** VAT rate expressed as basis points. 2500n = 25.00%, 0n = 0%. */
export type BasisPoints = bigint;

/** ISO 8601 date-time string (UTC). Example: "2026-01-01T00:00:00Z" */
export type IsoDateTime = string;

/** ISO 8601 date string. Example: "2026-01-01" */
export type IsoDate = string;

/** ISO 3166-1 alpha-2 country code. Example: "DK", "NO", "DE" */
export type CountryCode = string;

/** ISO 4217 currency code. Example: "DKK", "EUR", "NOK" */
export type CurrencyCode = string;

// ---------------------------------------------------------------------------
// Jurisdiction registry
// ---------------------------------------------------------------------------

/**
 * Union of all supported jurisdiction codes.
 * Add a new jurisdiction here when implementing a new plugin.
 * This is the only place in core that must be updated per jurisdiction.
 */
export type JurisdictionCode = 'DK' | 'NO' | 'DE';

/**
 * Map from JurisdictionCode to the jurisdiction-specific fields on a TaxReturn.
 * Each jurisdiction plugin defines its own field shape here.
 *
 * Example: DK requires SKAT rubrik fields for EU intra-community transactions.
 */
export interface JurisdictionReturnFields {
  DK: DkReturnFields;
  NO: Record<string, never>; // Phase 3: define when implementing Norway plugin
  DE: Record<string, never>; // Phase 3: define when implementing Germany plugin
}

// ---------------------------------------------------------------------------
// Danish-specific return fields (rubrik schema)
// ---------------------------------------------------------------------------

/**
 * Danish SKAT rubrik fields required on every DK VAT return.
 * Values are in DKK (not øre) as SKAT reports in whole DKK.
 * Derived field `netVatAmount` and `resultType` are computed, not stored.
 *
 * Rubrik A = EU intra-community purchases (acquistions)
 * Rubrik B = EU intra-community sales (supplies)
 */
export interface DkReturnFields {
  /** Rubrik A: Value of EU goods purchases (intra-community acquisitions), DKK */
  rubrikAGoodsEuPurchaseValue: Money;
  /** Rubrik A: Value of EU services purchases (reverse charge), DKK */
  rubrikAServicesEuPurchaseValue: Money;
  /** Rubrik B: Value of EU goods sales (intra-community supplies), DKK */
  rubrikBGoodsEuSaleValue: Money;
  /** Rubrik B: Value of EU services sales, DKK */
  rubrikBServicesEuSaleValue: Money;
}

// ---------------------------------------------------------------------------
// Filing types and statuses
// ---------------------------------------------------------------------------

/** The type of VAT filing being made. */
export type FilingType = 'regular' | 'correction' | 'nil';

/** Lifecycle status of a tax return. */
export type ReturnStatus = 'draft' | 'submitted' | 'accepted' | 'rejected';

/** Whether the net VAT results in an amount owed or a refund claim. */
export type VatResultType = 'payable' | 'claimable';

/** Cadence of VAT filing obligations. */
export type FilingCadence = 'monthly' | 'quarterly' | 'annual';

/** Period status — open for transactions, closed for filing, or submitted. */
export type TaxPeriodStatus = 'open' | 'closed' | 'submitted';

// ---------------------------------------------------------------------------
// Core entities
// ---------------------------------------------------------------------------

/**
 * A physical or legal address.
 */
export interface Address {
  /** Street address line 1 */
  line1: string;
  /** Street address line 2 (optional) */
  line2?: string;
  /** City */
  city: string;
  /** Postal/ZIP code */
  postalCode: string;
  /** ISO 3166-1 alpha-2 country code */
  countryCode: CountryCode;
}

/**
 * A legal entity — the taxpayer, a customer, or a supplier.
 */
export interface Counterparty {
  /** Immutable system identifier (UUID) */
  id: string;
  /** Legal name of the entity */
  name: string;
  /** Role in relation to the filing entity */
  role: 'self' | 'customer' | 'supplier';
  /** Domestic VAT registration number (format varies by jurisdiction) */
  vatNumber: string | null;
  /** EU VAT number for intra-community transactions (VIES-validated) */
  euVatNumber: string | null;
  /** Danish CVR number (8 digits) — DK-specific but stored here for practicality */
  cvrNumber: string | null;
  /** ISO 3166-1 alpha-2 country code of the entity's registration */
  countryCode: CountryCode;
  /** Registered address */
  address: Address;
  /** Whether the EU VAT number has been validated via VIES */
  viesValidated: boolean;
  /** ISO 8601 UTC timestamp of last VIES validation, or null if not validated */
  viesValidatedAt: IsoDateTime | null;
}

/**
 * A time window covering one VAT filing period.
 */
export interface TaxPeriod {
  /** Immutable system identifier (UUID) */
  id: string;
  /** Jurisdiction this period belongs to */
  jurisdictionCode: JurisdictionCode;
  /** First day of the period (inclusive), ISO 8601 date */
  startDate: IsoDate;
  /** Last day of the period (inclusive), ISO 8601 date */
  endDate: IsoDate;
  /** Expected filing cadence for this period */
  cadence: FilingCadence;
  /** Deadline for filing this period, ISO 8601 date */
  dueDate: IsoDate;
  /** Number of calendar days in the period (endDate − startDate + 1) */
  periodDays: number;
  /** Lifecycle status of this period */
  status: TaxPeriodStatus;
}

/**
 * The top-level aggregate for a single VAT filing.
 * Generic over JurisdictionCode so that jurisdiction-specific fields are type-safe.
 *
 * @template J - The jurisdiction code (e.g. 'DK')
 */
export interface TaxReturn<J extends JurisdictionCode = JurisdictionCode> {
  /** Immutable system identifier (UUID) */
  id: string;
  /** Jurisdiction this return is filed in */
  jurisdiction: J;
  /** The counterparty (taxpayer) filing this return */
  counterpartyId: string;
  /** The tax period this return covers */
  taxPeriodId: string;
  /** Type of filing */
  filingType: FilingType;
  /** Total output VAT collected (sales VAT), in smallest currency unit */
  outputVat: Money;
  /** Total deductible input VAT (purchase VAT), in smallest currency unit */
  inputVatDeductible: Money;
  /**
   * Net VAT = outputVat − inputVatDeductible.
   * Derived field — always equals the difference; stored for query convenience.
   */
  netVat: Money;
  /** Whether net VAT is owed to authority or claimable as refund */
  resultType: VatResultType;
  /** Current lifecycle status */
  status: ReturnStatus;
  /** ISO 8601 UTC timestamp when submitted to authority, null if not yet submitted */
  submittedAt: IsoDateTime | null;
  /** Jurisdiction-specific fields (e.g. Danish rubrik data) */
  jurisdictionFields: JurisdictionReturnFields[J];
  /**
   * If this is a correction, the ID of the original TaxReturn being corrected.
   * Null for original filings.
   */
  correctionOf: string | null;
  /** ISO 8601 UTC timestamp of record creation. Immutable. */
  createdAt: IsoDateTime;
}

/**
 * A single line item on an invoice.
 */
export interface InvoiceLine {
  /** 1-based line number */
  lineNumber: number;
  /** Invoice this line belongs to */
  invoiceId: string;
  /** Human-readable description of the goods or service */
  description: string;
  /** Quantity in the smallest measurable unit (×1000 for 3 decimal places) */
  quantity: bigint;
  /** Unit price excluding VAT, in smallest currency unit */
  unitPrice: Money;
  /** Tax code determining VAT rate and treatment */
  taxCode: string;
  /** Net amount excluding VAT (quantity × unitPrice adjusted for unit scale) */
  netAmount: Money;
  /** VAT amount calculated from taxCode rate */
  vatAmount: Money;
}

/**
 * A legal invoice document representing a supply transaction.
 */
export interface Invoice {
  /** Immutable system identifier (UUID) */
  id: string;
  /** Whether this is an inbound (purchase) or outbound (sale) invoice */
  direction: 'inbound' | 'outbound';
  /** Counterparty — supplier for inbound, customer for outbound */
  counterpartyId: string;
  /** Authority-assigned or internally generated invoice number */
  invoiceNumber: string;
  /** Date the invoice was issued, ISO 8601 */
  invoiceDate: IsoDate;
  /**
   * Tax point date — the date VAT liability arises.
   * May differ from invoiceDate (e.g. for continuous supplies).
   */
  taxPointDate: IsoDate;
  /** Line items */
  lines: InvoiceLine[];
  /** Total amount excluding VAT, in smallest currency unit */
  totalNet: Money;
  /** Total VAT amount, in smallest currency unit */
  totalVat: Money;
  /** Total amount including VAT, in smallest currency unit */
  totalGross: Money;
  /** ISO 4217 currency code */
  currencyCode: CurrencyCode;
  /** PEPPOL BIS 3.0 document ID if this is an e-invoice, null otherwise */
  peppolDocumentId: string | null;
  /** Jurisdiction whose rules govern this invoice */
  jurisdictionCode: JurisdictionCode;
}

/**
 * Type of EU intra-community transaction for rubrik classification.
 */
export type EuTransactionType = 'goods' | 'services';

/**
 * An economic event that affects the VAT position for a filing period.
 * Each Invoice generates one or more Transactions.
 */
export interface Transaction {
  /** Immutable system identifier (UUID) */
  id: string;
  /** Source invoice */
  invoiceId: string;
  /**
   * Tax return this transaction has been assigned to.
   * Null until the tax period is closed and returns are prepared.
   */
  taxReturnId: string | null;
  /** Date the transaction occurred, ISO 8601 */
  transactionDate: IsoDate;
  /** Tax code determining VAT treatment */
  taxCode: string;
  /** Taxable base amount, in smallest currency unit */
  taxableAmount: Money;
  /** Computed VAT amount, in smallest currency unit */
  vatAmount: Money;
  /** Whether this is input (purchase) or output (sale) VAT */
  direction: 'input' | 'output';
  /**
   * Whether the reverse charge mechanism applies.
   * If true, the buyer (not seller) accounts for the VAT.
   */
  reverseCharge: boolean;
  /**
   * EU intra-community transaction type for rubrik A/B classification.
   * Null for domestic transactions.
   */
  euTransactionType: EuTransactionType | null;
  /** Counterparty for this transaction */
  counterpartyId: string;
}

/**
 * Encodes the VAT treatment and rate for a supply of goods or services.
 */
export interface TaxCode {
  /** Unique code identifier, conventionally prefixed with jurisdiction (e.g. 'DK_STANDARD') */
  code: string;
  /** Jurisdiction this tax code belongs to */
  jurisdictionCode: JurisdictionCode;
  /** Category of VAT treatment */
  rateType: 'standard' | 'reduced' | 'zero' | 'exempt';
  /**
   * VAT rate in basis points. 2500n = 25.00%, 0n = 0%.
   * Exempt supplies have rate 0n but differ from zero-rated in VAT recovery rules.
   */
  rate: BasisPoints;
  /** Whether the reverse charge mechanism can apply for this code */
  reverseChargeApplicable: boolean;
  /** Human-readable description */
  description: string;
}

/**
 * Describes the legal VAT filing obligation for a counterparty in a jurisdiction.
 */
export interface FilingObligation {
  /** Immutable system identifier (UUID) */
  id: string;
  /** The counterparty (taxpayer) this obligation applies to */
  counterpartyId: string;
  /** Jurisdiction of the obligation */
  jurisdictionCode: JurisdictionCode;
  /** Filing cadence determined by revenue thresholds */
  cadence: FilingCadence;
  /** Annual revenue used to determine cadence, in smallest currency unit */
  annualRevenue: Money;
  /**
   * Whether the revenue threshold for an elevated cadence has been breached.
   * Example: >50M DKK triggers monthly filing in Denmark.
   */
  thresholdBreached: boolean;
  /** Date from which this obligation is effective, ISO 8601 */
  effectiveFrom: IsoDate;
  /** Date to which this obligation is effective, ISO 8601. Null = currently active. */
  effectiveTo: IsoDate | null;
}

/**
 * Represents a formal correction to a previously submitted TaxReturn.
 * Corrections are new events — originals are never mutated.
 */
export interface Correction {
  /** Immutable system identifier (UUID) */
  id: string;
  /** The return being corrected */
  originalReturnId: string;
  /** The new return containing the corrected values */
  correctionReturnId: string;
  /** Mandatory explanation of why the correction is being made */
  reason: string;
  /** Credit note invoice that triggered this correction, if applicable */
  creditNoteInvoiceId: string | null;
  /** ISO 8601 UTC timestamp of correction creation. Immutable. */
  createdAt: IsoDateTime;
  /** User or system agent that created the correction */
  createdBy: string;
}

/**
 * Valid audit event types. Extend this union as new event types are needed.
 */
export type AuditEventType =
  | 'TRANSACTION_CREATED'
  | 'INVOICE_CREATED'
  | 'INVOICE_VALIDATED'
  | 'TAX_RETURN_DRAFTED'
  | 'TAX_RETURN_SUBMITTED'
  | 'TAX_RETURN_ACCEPTED'
  | 'TAX_RETURN_REJECTED'
  | 'CORRECTION_CREATED'
  | 'PERIOD_CLOSED'
  | 'VIES_VALIDATION_PERFORMED'
  | 'DRR_REPORT_SENT';   // Phase 2: ViDA Digital Reporting Requirement

/**
 * An immutable audit log entry. Written before the state change it records takes effect.
 * Complies with Danish Bogføringsloven (5-year immutable retention).
 */
export interface AuditEvent {
  /** Immutable system identifier (UUID) */
  id: string;
  /** The type of event that occurred */
  eventType: AuditEventType;
  /** The type of domain aggregate affected (e.g. 'TaxReturn', 'Invoice') */
  aggregateType: string;
  /** ID of the affected aggregate */
  aggregateId: string;
  /** Snapshot of relevant state at the time of the event */
  payload: Record<string, unknown>;
  /** Jurisdiction context for this event */
  jurisdictionCode: JurisdictionCode;
  /** ISO 8601 UTC timestamp of when the event occurred */
  occurredAt: IsoDateTime;
  /** User ID or agent identifier that triggered the event */
  actorId: string;
  /**
   * Links related events together (e.g. all events in a correction chain).
   * Use a shared UUID for all events in the same logical operation.
   */
  correlationId: string;
}

// ---------------------------------------------------------------------------
// Jurisdiction Plugin interfaces
// ---------------------------------------------------------------------------

/**
 * Result of a validation operation.
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

/** A validation error that must be resolved before filing. */
export interface ValidationError {
  field: string;
  code: string;
  message: string;
}

/** A validation warning — filing can proceed but attention is recommended. */
export interface ValidationWarning {
  field: string;
  code: string;
  message: string;
}

/**
 * Input for creating or validating a tax return, before jurisdiction-specific
 * fields are resolved.
 */
export type TaxReturnInput<J extends JurisdictionCode = JurisdictionCode> = {
  counterpartyId: string;
  taxPeriodId: string;
  filingType: FilingType;
  outputVat: Money;
  inputVatDeductible: Money;
  jurisdictionFields: JurisdictionReturnFields[J];
  correctionOf?: string;
};

/**
 * Registry of tax rates for a jurisdiction.
 */
export interface TaxRateRegistry<_J extends JurisdictionCode> {
  /** Standard VAT rate in basis points (e.g. 2500n = 25%) */
  getStandardRate(): BasisPoints;
  /** Reduced VAT rates by category name */
  getReducedRates(): Record<string, BasisPoints>;
  /** Rate for zero-rated supplies (always 0n, but VAT recovery is allowed) */
  getZeroRate(): BasisPoints;
  /** All tax codes defined for this jurisdiction */
  getAllTaxCodes(): TaxCode[];
  /** Look up a specific tax code */
  getTaxCode(code: string): TaxCode | undefined;
}

/**
 * Validation rules for a jurisdiction.
 */
export interface ValidationRuleSet<J extends JurisdictionCode> {
  /**
   * Validate a complete tax return filing against authority rules.
   * Includes cross-field validation (e.g. net VAT consistency).
   */
  validateFiling(data: TaxReturnInput<J>): ValidationResult;
  /** Validate a counterparty's VAT number format for this jurisdiction */
  validateCounterparty(c: Counterparty): ValidationResult;
  /** Validate a tax code is applicable in the given transaction context */
  validateTaxCode(code: string, context: { direction: 'input' | 'output'; reverseCharge: boolean }): ValidationResult;
}

/**
 * Determines filing schedules and deadlines for a counterparty.
 */
export interface FilingScheduleProvider<_J extends JurisdictionCode> {
  /**
   * Determine the filing cadence for a counterparty based on their revenue.
   * Applies jurisdiction-specific threshold rules.
   */
  getCadence(counterparty: Counterparty, annualRevenue: Money): FilingCadence;
  /**
   * Calculate the filing deadline for a given tax period.
   */
  getDeadline(period: TaxPeriod): IsoDate;
  /**
   * Generate the sequence of tax periods for a given calendar year.
   */
  getPeriodsForYear(year: number, cadence: FilingCadence): TaxPeriod[];
}

/**
 * Formats tax return data into the authority's required submission format.
 */
export interface ReportFormatter<J extends JurisdictionCode> {
  /**
   * Format a completed tax return for submission to the tax authority.
   * Returns a Buffer containing the formatted report (XML, JSON, etc.)
   */
  formatReturn(taxReturn: TaxReturn<J>): Buffer;
  /** MIME type of the formatted output (e.g. 'application/xml') */
  readonly outputMimeType: string;
}

/**
 * Client for interacting with the jurisdiction's tax authority API.
 */
export interface AuthorityApiClient<_J extends JurisdictionCode> {
  /** Submit a formatted tax return to the authority. Returns a receipt/reference. */
  submitReturn(payload: Buffer): Promise<AuthoritySubmissionReceipt>;
  /** Check the status of a previously submitted return */
  checkStatus(referenceId: string): Promise<AuthorityReturnStatus>;
  /** Validate a VAT number with the authority */
  validateVatNumber(vatNumber: string): Promise<VatNumberValidationResult>;
}

/** Receipt from authority on successful submission */
export interface AuthoritySubmissionReceipt {
  referenceId: string;
  acceptedAt: IsoDateTime;
  authorityMessage: string | null;
}

/** Status of a submitted return as reported by the authority */
export interface AuthorityReturnStatus {
  referenceId: string;
  status: 'pending' | 'accepted' | 'rejected';
  message: string | null;
  processedAt: IsoDateTime | null;
}

/** Result of validating a VAT number with the authority */
export interface VatNumberValidationResult {
  vatNumber: string;
  valid: boolean;
  name: string | null;
  address: string | null;
  validatedAt: IsoDateTime;
}

/**
 * ViDA (VAT in the Digital Age) configuration for a jurisdiction.
 * Controls Phase 2 behaviour — set all flags to false until the jurisdiction
 * has active ViDA obligations.
 */
export interface VidaConfig {
  /**
   * Whether Digital Reporting Requirements (DRR) are active.
   * When true, each transaction is reported to the authority in near-real-time.
   * EU mandate: 2028.
   */
  drrEnabled: boolean;
  /** Endpoint for DRR transaction reporting. Required if drrEnabled is true. */
  drrEndpoint: string | null;
  /** Transaction types that trigger DRR reporting */
  drrTransactionTypes: EuTransactionType[];
  /** Whether the extended One Stop Shop (OSS) scheme is active */
  extendedOssEnabled: boolean;
  /** Whether platform economy deemed supplier rules are active */
  platformDeemedSupplierEnabled: boolean;
  /**
   * ISO 8601 date from which ViDA obligations are effective.
   * Null if not yet determined.
   */
  effectiveFrom: IsoDate | null;
}

/**
 * The primary plugin interface that every jurisdiction implementation must satisfy.
 * Adding a new country = new implementation of this interface + registration.
 * Zero changes to any other core code.
 *
 * @template J - The jurisdiction code this plugin handles
 */
export interface JurisdictionPlugin<J extends JurisdictionCode> {
  /** Jurisdiction code this plugin handles */
  readonly code: J;
  /** Human-readable jurisdiction name (e.g. "Denmark", "Norway") */
  readonly displayName: string;
  /** ISO 4217 currency code for this jurisdiction (e.g. 'DKK') */
  readonly currencyCode: CurrencyCode;
  /** Name of the smallest currency unit (e.g. 'øre', 'cent', 'øre') */
  readonly smallestCurrencyUnit: string;
  /** Tax rate registry for this jurisdiction */
  taxRates: TaxRateRegistry<J>;
  /** Validation rules for this jurisdiction */
  validationRules: ValidationRuleSet<J>;
  /** Filing schedule rules for this jurisdiction */
  filingSchedule: FilingScheduleProvider<J>;
  /** Report formatter for authority submission */
  reportFormatter: ReportFormatter<J>;
  /** Authority API client */
  authorityClient: AuthorityApiClient<J>;
  /** ViDA configuration — all false until Phase 2 */
  vidaConfig: VidaConfig;
}

// ---------------------------------------------------------------------------
// Plugin registry
// ---------------------------------------------------------------------------

/**
 * Runtime registry of jurisdiction plugins.
 * Resolved at startup; all business logic depends on JurisdictionPlugin interface only.
 */
export interface JurisdictionRegistry {
  /** Register a jurisdiction plugin */
  register<J extends JurisdictionCode>(plugin: JurisdictionPlugin<J>): void;
  /** Resolve a plugin by jurisdiction code. Throws if not registered. */
  resolve<J extends JurisdictionCode>(code: J): JurisdictionPlugin<J>;
  /** List all registered jurisdiction codes */
  listRegistered(): JurisdictionCode[];
}
