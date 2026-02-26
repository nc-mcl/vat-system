package com.netcompany.vat.api.reporting;

import com.netcompany.vat.api.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReportingController}.
 *
 * <p>Uses plain Mockito — no Spring context required.
 */
class ReportingControllerTest {

    @Mock private VatReportingService reportingService;
    @Mock private SaftReportingService saftService;
    @Mock private ViDaDrrService drrService;

    private ReportingController controller;

    private final UUID returnId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();

    private DkMomsangivelse sampleMomsangivelse;
    private VatReportPayload samplePayload;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new ReportingController(reportingService, saftService, drrService);

        sampleMomsangivelse = new DkMomsangivelse(
                returnId, periodId, "DK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                250_000L,  // outputVatAmount
                50_000L,   // inputVatDeductibleAmount
                0L,        // vatOnGoodsPurchasesAbroadAmount (Box 3)
                100_000L,  // vatOnServicesPurchasesAbroadAmount (Box 4)
                0L,        // rubrikAGoodsEuPurchaseValue
                400_000L,  // rubrikAServicesEuPurchaseValue
                0L,        // rubrikBGoodsEuSaleValue
                0L,        // rubrikBServicesEuSaleValue
                0L,        // rubrikCOtherVatExemptSuppliesValue
                200_000L,  // netVatAmount
                "PAYABLE",
                0L,
                "ACCEPTED",
                "Phase 1 approximation: ..."
        );

        samplePayload = new VatReportPayload(
                returnId,
                Instant.parse("2026-02-26T12:00:00Z"),
                VatReportingService.FORMAT_DK_MOMSANGIVELSE_JSON,
                sampleMomsangivelse
        );
    }

    // =========================================================================
    // GET /momsangivelse
    // =========================================================================

    @Test
    void getMomsangivelse_found_returns200() {
        when(reportingService.generateMomsangivelse(returnId)).thenReturn(sampleMomsangivelse);

        ResponseEntity<DkMomsangivelse> response = controller.getMomsangivelse(returnId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().returnId()).isEqualTo(returnId);
        assertThat(response.getBody().outputVatAmount()).isEqualTo(250_000L);
        assertThat(response.getBody().netVatAmount()).isEqualTo(200_000L);
        assertThat(response.getBody().resultType()).isEqualTo("PAYABLE");
        assertThat(response.getBody().rubrikAServicesEuPurchaseValue()).isEqualTo(400_000L);
        assertThat(response.getBody().vatOnServicesPurchasesAbroadAmount()).isEqualTo(100_000L);
    }

    @Test
    void getMomsangivelse_notFound_throwsEntityNotFoundException() {
        when(reportingService.generateMomsangivelse(returnId))
                .thenThrow(new EntityNotFoundException("VAT return not found: " + returnId));

        assertThrows(EntityNotFoundException.class, () -> controller.getMomsangivelse(returnId));
    }

    @Test
    void getMomsangivelse_wrongJurisdiction_throwsIllegalArgumentException() {
        when(reportingService.generateMomsangivelse(returnId))
                .thenThrow(new IllegalArgumentException(
                        "DK momsangivelse report is only available for jurisdiction DK; got: NO"));

        assertThrows(IllegalArgumentException.class, () -> controller.getMomsangivelse(returnId));
    }

    // =========================================================================
    // GET /payload
    // =========================================================================

    @Test
    void getPayload_found_returns200WithMetadata() {
        when(reportingService.generatePayload(returnId)).thenReturn(samplePayload);

        ResponseEntity<VatReportPayload> response = controller.getPayload(returnId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().returnId()).isEqualTo(returnId);
        assertThat(response.getBody().format()).isEqualTo(VatReportingService.FORMAT_DK_MOMSANGIVELSE_JSON);
        assertThat(response.getBody().generatedAt()).isEqualTo(Instant.parse("2026-02-26T12:00:00Z"));
        assertThat(response.getBody().momsangivelse()).isNotNull();
        assertThat(response.getBody().momsangivelse().status()).isEqualTo("ACCEPTED");
    }

    @Test
    void getPayload_notFound_throwsEntityNotFoundException() {
        when(reportingService.generatePayload(returnId))
                .thenThrow(new EntityNotFoundException("VAT return not found: " + returnId));

        assertThrows(EntityNotFoundException.class, () -> controller.getPayload(returnId));
    }

    // =========================================================================
    // Phase 2 stubs — all should propagate UnsupportedOperationException
    // =========================================================================

    @Test
    void getSaft_throwsUnsupportedOperationException_phase2Stub() {
        when(saftService.generateSaftXml(returnId))
                .thenThrow(new UnsupportedOperationException("SAF-T export is not available in Phase 1."));

        assertThrows(UnsupportedOperationException.class, () -> controller.getSaft(returnId));
    }

    @Test
    void submitDrr_throwsUnsupportedOperationException_phase2Stub() {
        org.mockito.Mockito.doThrow(new UnsupportedOperationException("ViDA DRR not available in Phase 1."))
                .when(drrService).submitDrr(returnId);

        assertThrows(UnsupportedOperationException.class, () -> controller.submitDrr(returnId));
    }

    @Test
    void getEuSalgsangivelse_throwsUnsupportedOperationException_phase2Stub() {
        org.mockito.Mockito.doThrow(new UnsupportedOperationException("EU-salgsangivelse not available in Phase 1."))
                .when(drrService).generateEuSalgsangivelse(returnId);

        assertThrows(UnsupportedOperationException.class,
                () -> controller.getEuSalgsangivelse(returnId));
    }

    // =========================================================================
    // VatReportingService unit tests
    // =========================================================================

    @Test
    void reportPayload_hasCorrectFormat() {
        assertThat(VatReportingService.FORMAT_DK_MOMSANGIVELSE_JSON)
                .isEqualTo("DK_MOMSANGIVELSE_JSON");
    }
}
