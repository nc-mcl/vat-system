package com.netcompany.vat.skatclient;

import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.skatclient.dto.SkatSubmissionRequest;
import com.netcompany.vat.skatclient.mapper.SkatMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkatMapperTest {

    private static final UUID PERIOD_ID = UUID.randomUUID();
    private static final UUID RETURN_ID = UUID.randomUUID();

    private VatReturn buildReturn(long outputVatOere, long inputVatOere,
                                   long rubrikAGoods, long rubrikAServices,
                                   long rubrikBGoods, long rubrikBServices,
                                   long rubrikCOther) {
        Map<String, Object> fields = Map.of(
                "rubrikAGoodsEuPurchaseValue",       rubrikAGoods,
                "rubrikAServicesEuPurchaseValue",    rubrikAServices,
                "rubrikBGoodsEuSaleValue",           rubrikBGoods,
                "rubrikBServicesEuSaleValue",        rubrikBServices,
                "rubrikCOtherVatExemptSuppliesValue",rubrikCOther
        );
        return VatReturn.of(
                RETURN_ID, JurisdictionCode.DK, PERIOD_ID,
                MonetaryAmount.ofOere(outputVatOere),
                MonetaryAmount.ofOere(inputVatOere),
                VatReturnStatus.DRAFT,
                fields
        );
    }

    @Test
    void toSubmissionRequest_convertsOereToDkk_correctly() {
        // 25000 øre = 250.00 DKK
        VatReturn vatReturn = buildReturn(25_000L, 0L, 0L, 0L, 0L, 0L, 0L);

        SkatSubmissionRequest request = SkatMapper.toSubmissionRequest(vatReturn);

        assertThat(request.outputVatDkk()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(request.inputVatDkk()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(request.netVatDkk()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    void toSubmissionRequest_convertsRubrikFields_correctly() {
        // rubrikA services: 200000 øre = 2000.00 DKK
        // rubrikB services: 150000 øre = 1500.00 DKK
        // rubrikC: 50000 øre = 500.00 DKK
        VatReturn vatReturn = buildReturn(
                75_000L, 0L,
                0L, 200_000L,      // rubrikA goods / services
                0L, 150_000L,      // rubrikB goods / services
                50_000L            // rubrikC
        );

        SkatSubmissionRequest request = SkatMapper.toSubmissionRequest(vatReturn);

        assertThat(request.rubrikAGoodsDkk()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(request.rubrikAServicesDkk()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(request.rubrikBGoodsDkk()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(request.rubrikBServicesDkk()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(request.rubrikCOtherDkk()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void toSubmissionRequest_populatesCorrelationIds() {
        VatReturn vatReturn = buildReturn(10_000L, 0L, 0L, 0L, 0L, 0L, 0L);

        SkatSubmissionRequest request = SkatMapper.toSubmissionRequest(vatReturn);

        assertThat(request.returnId()).isEqualTo(RETURN_ID.toString());
        assertThat(request.periodId()).isEqualTo(PERIOD_ID.toString());
        assertThat(request.jurisdictionCode()).isEqualTo("DK");
    }

    @Test
    void toSubmissionRequest_noOioublFields_present() {
        VatReturn vatReturn = buildReturn(10_000L, 0L, 0L, 0L, 0L, 0L, 0L);

        SkatSubmissionRequest request = SkatMapper.toSubmissionRequest(vatReturn);

        // OIOUBL 2.1 was phased out May 15 2026 — no OIOUBL fields should exist
        String requestString = request.toString();
        assertThat(requestString).doesNotContainIgnoringCase("oioubl");
        assertThat(requestString).doesNotContainIgnoringCase("ubl");
    }

    @Test
    void oereToDkk_correctConversion() {
        assertThat(SkatMapper.oereToDkk(0L)).isEqualByComparingTo("0.00");
        assertThat(SkatMapper.oereToDkk(100L)).isEqualByComparingTo("1.00");
        assertThat(SkatMapper.oereToDkk(25_000L)).isEqualByComparingTo("250.00");
        assertThat(SkatMapper.oereToDkk(1L)).isEqualByComparingTo("0.01");
        assertThat(SkatMapper.oereToDkk(1_000_000L)).isEqualByComparingTo("10000.00");
    }

    @Test
    void toSubmissionRequest_negativeNetVat_claimableReturn() {
        // More input VAT than output → claimable (negative net)
        VatReturn vatReturn = buildReturn(10_000L, 50_000L, 0L, 0L, 0L, 0L, 0L);

        SkatSubmissionRequest request = SkatMapper.toSubmissionRequest(vatReturn);

        // Net VAT: 10000 - 50000 = -40000 øre = -400.00 DKK
        assertThat(request.netVatDkk()).isEqualByComparingTo(new BigDecimal("-400.00"));
        assertThat(request.resultType()).isEqualTo("CLAIMABLE");
    }
}
