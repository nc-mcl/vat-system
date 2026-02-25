package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.config.JurisdictionRegistry;
import com.netcompany.vat.api.dto.AssembleReturnRequest;
import com.netcompany.vat.api.dto.VatReturnResponse;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.api.exception.InvalidPeriodStateException;
import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.ResultType;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import com.netcompany.vat.persistence.repository.TransactionRepository;
import com.netcompany.vat.persistence.repository.VatReturnRepository;
import com.netcompany.vat.taxengine.TaxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VatReturnControllerTest {

    @Mock private TaxPeriodRepository taxPeriodRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private VatReturnRepository vatReturnRepository;
    @Mock private AuditLogger auditLogger;
    @Mock private JurisdictionRegistry registry;
    @Mock private TaxEngine taxEngine;

    private VatReturnController controller;

    private final UUID periodId = UUID.randomUUID();
    private final UUID returnId = UUID.randomUUID();
    private TaxPeriod openPeriod;
    private VatReturn draftReturn;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new VatReturnController(
                taxPeriodRepository, transactionRepository, vatReturnRepository, auditLogger, registry);

        openPeriod = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);

        draftReturn = new VatReturn(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L),
                MonetaryAmount.ofOere(50_000L),
                MonetaryAmount.ofOere(200_000L),
                ResultType.PAYABLE,
                MonetaryAmount.ZERO,
                VatReturnStatus.DRAFT,
                Collections.emptyMap()
        );

        when(registry.getTaxEngine(JurisdictionCode.DK)).thenReturn(taxEngine);
    }

    @Test
    void assembleReturn_happyPath_returns201() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));
        when(vatReturnRepository.findByPeriod(periodId, JurisdictionCode.DK)).thenReturn(Optional.empty());
        when(transactionRepository.findByPeriod(periodId)).thenReturn(List.of());
        when(taxEngine.assembleReturn(any(), eq(openPeriod))).thenReturn(Result.ok(draftReturn));
        when(vatReturnRepository.save(draftReturn)).thenReturn(draftReturn);
        when(taxPeriodRepository.updateStatus(periodId, TaxPeriodStatus.FILED)).thenReturn(openPeriod);

        AssembleReturnRequest req = new AssembleReturnRequest(periodId.toString(), "DK");
        ResponseEntity<VatReturnResponse> response = controller.assembleReturn(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().outputVat()).isEqualTo(250_000L);
        assertThat(response.getBody().netVat()).isEqualTo(200_000L);
        assertThat(response.getBody().resultType()).isEqualTo("PAYABLE");
        verify(auditLogger).log(any());
        verify(vatReturnRepository).save(draftReturn);
        verify(taxPeriodRepository).updateStatus(periodId, TaxPeriodStatus.FILED);
    }

    @Test
    void assembleReturn_periodNotFound_throwsEntityNotFoundException() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.empty());

        AssembleReturnRequest req = new AssembleReturnRequest(periodId.toString(), "DK");

        assertThrows(EntityNotFoundException.class, () -> controller.assembleReturn(req));
    }

    @Test
    void assembleReturn_returnAlreadyExists_throws409() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));
        when(vatReturnRepository.findByPeriod(periodId, JurisdictionCode.DK))
                .thenReturn(Optional.of(draftReturn));

        AssembleReturnRequest req = new AssembleReturnRequest(periodId.toString(), "DK");

        assertThrows(InvalidPeriodStateException.class, () -> controller.assembleReturn(req));
    }

    @Test
    void submitReturn_happyPath_returns202() {
        when(vatReturnRepository.findById(returnId)).thenReturn(Optional.of(draftReturn));
        when(vatReturnRepository.updateStatus(eq(returnId), eq(VatReturnStatus.SUBMITTED), any()))
                .thenReturn(draftReturn);

        ResponseEntity<Map<String, Object>> response = controller.submitReturn(returnId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("status", "SUBMITTED");
        verify(auditLogger).log(any());
    }

    @Test
    void submitReturn_notFound_throwsEntityNotFoundException() {
        when(vatReturnRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> controller.submitReturn(returnId));
    }

    @Test
    void submitReturn_notDraft_throws409() {
        VatReturn submittedReturn = new VatReturn(
                returnId, JurisdictionCode.DK, periodId,
                MonetaryAmount.ofOere(250_000L), MonetaryAmount.ofOere(50_000L),
                MonetaryAmount.ofOere(200_000L), ResultType.PAYABLE, MonetaryAmount.ZERO,
                VatReturnStatus.SUBMITTED, Collections.emptyMap());
        when(vatReturnRepository.findById(returnId)).thenReturn(Optional.of(submittedReturn));

        assertThrows(InvalidPeriodStateException.class, () -> controller.submitReturn(returnId));
    }

    @Test
    void getReturn_found_returns200() {
        when(vatReturnRepository.findById(returnId)).thenReturn(Optional.of(draftReturn));

        ResponseEntity<VatReturnResponse> response = controller.getReturn(returnId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(returnId.toString());
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
    }

    @Test
    void getReturn_notFound_throwsEntityNotFoundException() {
        when(vatReturnRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> controller.getReturn(returnId));
    }
}
