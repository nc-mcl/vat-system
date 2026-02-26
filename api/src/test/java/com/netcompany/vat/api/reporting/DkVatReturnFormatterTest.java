package com.netcompany.vat.api.reporting;

import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.ResultType;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.taxengine.VatReturnAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DkVatReturnFormatter}.
 *
 * <p>Tests cover all rubrik field extractions and derived cross-field values
 * to ensure the formatter correctly maps the {@code jurisdictionFields} map
 * to the structured {@link DkMomsangivelse} record.
 */
class DkVatReturnFormatterTest {

    private DkVatReturnFormatter formatter;

    private final UUID returnId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();

    private TaxPeriod q1Period;

    @BeforeEach
    void setup() {
        formatter = new DkVatReturnFormatter();
        q1Period = new TaxPeriod(
                periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.FILED
        );
    }

    // -------------------------------------------------------------------------
    // Standard taxable sale — PAYABLE result
    // -------------------------------------------------------------------------

    @Test
    void format_standardSale_payable_populatesBoxesCorrectly() {
        // Q1: domestic sales, output VAT 250,000 øre; no purchases
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   0L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L),
                MonetaryAmount.ofOere(0L),
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.returnId()).isEqualTo(returnId);
        assertThat(result.periodId()).isEqualTo(periodId);
        assertThat(result.jurisdictionCode()).isEqualTo("DK");
        assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(result.outputVatAmount()).isEqualTo(250_000L);
        assertThat(result.inputVatDeductibleAmount()).isEqualTo(0L);
        assertThat(result.netVatAmount()).isEqualTo(250_000L);
        assertThat(result.resultType()).isEqualTo("PAYABLE");
        assertThat(result.claimAmount()).isEqualTo(0L);
        assertThat(result.status()).isEqualTo("ACCEPTED");
        // All rubriks zero — no cross-border activity
        assertThat(result.totalRubrikAValue()).isEqualTo(0L);
        assertThat(result.totalRubrikBValue()).isEqualTo(0L);
        assertThat(result.rubrikCOtherVatExemptSuppliesValue()).isEqualTo(0L);
        assertThat(result.phase1LimitationNote()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Reverse charge (EU services purchase) — Rubrik A services
    // -------------------------------------------------------------------------

    @Test
    void format_reverseCharge_euServices_populatesRubrikAServices() {
        // SaaS from Germany: net 2,000,000 øre; self-assessed VAT 500,000 øre (Box 4 = component of Box 1)
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 2_000_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   500_000L);

        // Net zero: self-assessed VAT 500,000 = deductible input 500,000
        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(500_000L),  // output (Box 1 includes Box 4)
                MonetaryAmount.ofOere(500_000L),  // input deductible (Box 2)
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.rubrikAServicesEuPurchaseValue()).isEqualTo(2_000_000L);
        assertThat(result.rubrikAGoodsEuPurchaseValue()).isEqualTo(0L);
        assertThat(result.vatOnServicesPurchasesAbroadAmount()).isEqualTo(500_000L);  // Box 4
        assertThat(result.vatOnGoodsPurchasesAbroadAmount()).isEqualTo(0L);           // Box 3
        assertThat(result.outputVatAmount()).isEqualTo(500_000L);                      // Box 1 = Box 4
        assertThat(result.inputVatDeductibleAmount()).isEqualTo(500_000L);             // Box 2
        assertThat(result.netVatAmount()).isEqualTo(0L);
        assertThat(result.resultType()).isEqualTo("ZERO");
        assertThat(result.totalRubrikAValue()).isEqualTo(2_000_000L);
    }

    // -------------------------------------------------------------------------
    // Zero-rated sale (EU B2B) — Rubrik B services (Phase 1 MVP assumption)
    // -------------------------------------------------------------------------

    @Test
    void format_zeroRatedEuSale_populatesRubrikBServices() {
        // Zero-rated EU service sale: net 1,000,000 øre; no VAT charged
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 1_000_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   0L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO,
                MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.rubrikBServicesEuSaleValue()).isEqualTo(1_000_000L);
        assertThat(result.rubrikBGoodsEuSaleValue()).isEqualTo(0L);
        assertThat(result.totalRubrikBValue()).isEqualTo(1_000_000L);
        assertThat(result.netVatAmount()).isEqualTo(0L);
        assertThat(result.resultType()).isEqualTo("ZERO");
    }

    // -------------------------------------------------------------------------
    // Exempt supply — Rubrik C
    // -------------------------------------------------------------------------

    @Test
    void format_exemptSupply_populatesRubrikC() {
        // Medical services (ML §13 exempt): 500,000 øre net value, no VAT
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    500_000L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   0L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.rubrikCOtherVatExemptSuppliesValue()).isEqualTo(500_000L);
        assertThat(result.totalRubrikAValue()).isEqualTo(0L);
        assertThat(result.totalRubrikBValue()).isEqualTo(0L);
        assertThat(result.resultType()).isEqualTo("ZERO");
    }

    // -------------------------------------------------------------------------
    // NIL return (nulindberetning)
    // -------------------------------------------------------------------------

    @Test
    void format_nilReturn_allFieldsZero() {
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   0L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.outputVatAmount()).isEqualTo(0L);
        assertThat(result.inputVatDeductibleAmount()).isEqualTo(0L);
        assertThat(result.netVatAmount()).isEqualTo(0L);
        assertThat(result.resultType()).isEqualTo("ZERO");
        assertThat(result.claimAmount()).isEqualTo(0L);
        assertThat(result.totalRubrikAValue()).isEqualTo(0L);
        assertThat(result.totalRubrikBValue()).isEqualTo(0L);
        assertThat(result.rubrikCOtherVatExemptSuppliesValue()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Mixed return: standard sale + reverse charge (typical B2B scenario)
    // -------------------------------------------------------------------------

    @Test
    void format_mixedReturn_standardPlusReverseCharge_payable() {
        // 3 standard DK sales: output VAT 1,000,000 øre
        // 1 EU SaaS purchase: rubrik A services 2,000,000; Box 4 VAT 500,000; input deductible 500,000
        // Net: 1,000,000 + 500,000 (Box 4) − 500,000 (Box 2) = 1,000,000 payable
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 2_000_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 0L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   500_000L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(1_500_000L),  // domestic output 1,000,000 + Box 4 500,000
                MonetaryAmount.ofOere(500_000L),     // Box 2 deduction = Box 4
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.outputVatAmount()).isEqualTo(1_500_000L);
        assertThat(result.inputVatDeductibleAmount()).isEqualTo(500_000L);
        assertThat(result.netVatAmount()).isEqualTo(1_000_000L);
        assertThat(result.resultType()).isEqualTo("PAYABLE");
        assertThat(result.claimAmount()).isEqualTo(0L);
        assertThat(result.rubrikAServicesEuPurchaseValue()).isEqualTo(2_000_000L);
        assertThat(result.vatOnServicesPurchasesAbroadAmount()).isEqualTo(500_000L);
    }

    // -------------------------------------------------------------------------
    // Null-safe: missing fields in jurisdictionFields map default to 0
    // -------------------------------------------------------------------------

    @Test
    void format_emptyJurisdictionFields_defaultsToZero() {
        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT,
                Map.of()  // intentionally empty
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.rubrikAGoodsEuPurchaseValue()).isEqualTo(0L);
        assertThat(result.rubrikAServicesEuPurchaseValue()).isEqualTo(0L);
        assertThat(result.rubrikBGoodsEuSaleValue()).isEqualTo(0L);
        assertThat(result.rubrikBServicesEuSaleValue()).isEqualTo(0L);
        assertThat(result.rubrikCOtherVatExemptSuppliesValue()).isEqualTo(0L);
        assertThat(result.vatOnGoodsPurchasesAbroadAmount()).isEqualTo(0L);
        assertThat(result.vatOnServicesPurchasesAbroadAmount()).isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // convenience accessors
    // -------------------------------------------------------------------------

    @Test
    void format_totalRubrikAAndBConvenienceAccessors() {
        Map<String, Object> fields = new HashMap<>();
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_GOODS,    300_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES, 700_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_GOODS,    400_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES, 600_000L);
        fields.put(VatReturnAssembler.FIELD_RUBRIK_C_OTHER,    0L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD, 75_000L);
        fields.put(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD,   175_000L);

        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L),
                MonetaryAmount.ofOere(250_000L),
                VatReturnStatus.ACCEPTED,
                Map.copyOf(fields)
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.totalRubrikAValue()).isEqualTo(1_000_000L); // 300k + 700k
        assertThat(result.totalRubrikBValue()).isEqualTo(1_000_000L); // 400k + 600k
        assertThat(result.vatOnGoodsPurchasesAbroadAmount()).isEqualTo(75_000L);
        assertThat(result.vatOnServicesPurchasesAbroadAmount()).isEqualTo(175_000L);
    }

    // -------------------------------------------------------------------------
    // Phase 1 limitation note is always populated
    // -------------------------------------------------------------------------

    @Test
    void format_phase1LimitationNote_alwaysPresent() {
        VatReturn vatReturn = VatReturn.of(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ZERO, MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT, Map.of()
        );

        DkMomsangivelse result = formatter.format(vatReturn, q1Period);

        assertThat(result.phase1LimitationNote())
                .contains("Phase 1 approximation")
                .contains("REVERSE_CHARGE")
                .contains("ZERO_RATED");
    }
}
