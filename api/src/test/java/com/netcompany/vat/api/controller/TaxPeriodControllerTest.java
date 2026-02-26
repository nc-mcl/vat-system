package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.config.JurisdictionRegistry;
import com.netcompany.vat.api.dto.OpenPeriodRequest;
import com.netcompany.vat.api.dto.TaxPeriodResponse;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxPeriodControllerTest {

    @Mock private TaxPeriodRepository taxPeriodRepository;
    @Mock private AuditLogger auditLogger;
    @Mock private JurisdictionRegistry registry;
    @Mock private JurisdictionPlugin plugin;

    private TaxPeriodController controller;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new TaxPeriodController(taxPeriodRepository, auditLogger, registry);

        when(registry.getPlugin(JurisdictionCode.DK)).thenReturn(plugin);
        when(plugin.calculateFilingDeadline(any())).thenReturn(LocalDate.of(2026, 6, 1));
    }

    @Test
    void openPeriod_happyPath_returns201() {
        TaxPeriod saved = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        when(taxPeriodRepository.save(any(), any())).thenReturn(saved);

        OpenPeriodRequest req = new OpenPeriodRequest("DK", "2026-01-01", "2026-03-31", "QUARTERLY");
        ResponseEntity<TaxPeriodResponse> response = controller.openPeriod(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jurisdictionCode()).isEqualTo("DK");
        assertThat(response.getBody().status()).isEqualTo("OPEN");
        verify(auditLogger).log(any());
        verify(taxPeriodRepository).save(any(), eq(LocalDate.of(2026, 6, 1)));
    }

    @Test
    void getPeriod_notFound_throwsEntityNotFoundException() {
        UUID id = UUID.randomUUID();
        when(taxPeriodRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> controller.getPeriod(id));
    }

    @Test
    void getPeriod_found_returns200() {
        UUID id = UUID.randomUUID();
        TaxPeriod period = new TaxPeriod(
                id, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        when(taxPeriodRepository.findById(id)).thenReturn(Optional.of(period));

        ResponseEntity<TaxPeriodResponse> response = controller.getPeriod(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(id.toString());
    }

    @Test
    void listPeriods_returnsList() {
        TaxPeriod period = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        when(taxPeriodRepository.findByJurisdiction(JurisdictionCode.DK)).thenReturn(List.of(period));

        ResponseEntity<List<TaxPeriodResponse>> response = controller.listPeriods("DK");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void openPeriod_unknownJurisdiction_throwsIllegalArgumentException() {
        // JurisdictionCode.fromString("UNKNOWN") throws IllegalArgumentException.
        // This is propagated from the controller (no try-catch) and mapped by
        // GlobalExceptionHandler to 400 Bad Request in the full web stack.
        OpenPeriodRequest req = new OpenPeriodRequest("UNKNOWN", "2026-01-01", "2026-03-31", "QUARTERLY");

        assertThrows(IllegalArgumentException.class, () -> controller.openPeriod(req));
    }

    @Test
    void openPeriod_allCadences_areAccepted() {
        // Verify that all four valid cadence values parse without error.
        String[] cadences = {"MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL"};
        TaxPeriod saved = new TaxPeriod(
                UUID.randomUUID(), JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);
        when(taxPeriodRepository.save(any(), any())).thenReturn(saved);

        for (String cadence : cadences) {
            OpenPeriodRequest req = new OpenPeriodRequest("DK", "2026-01-01", "2026-03-31", cadence);
            // Should not throw — all cadences are valid FilingCadence enum values
            assertDoesNotThrow(() -> controller.openPeriod(req),
                    "Expected openPeriod to succeed for cadence: " + cadence);
        }
    }

    @Test
    void openPeriod_invalidCadence_throwsIllegalArgumentException() {
        OpenPeriodRequest req = new OpenPeriodRequest("DK", "2026-01-01", "2026-03-31", "DAILY");

        assertThrows(IllegalArgumentException.class, () -> controller.openPeriod(req));
    }

    @Test
    void getPeriod_filedStatus_isReturnedCorrectly() {
        UUID id = UUID.randomUUID();
        TaxPeriod filed = new TaxPeriod(
                id, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.FILED);
        when(taxPeriodRepository.findById(id)).thenReturn(Optional.of(filed));

        ResponseEntity<TaxPeriodResponse> response = controller.getPeriod(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("FILED");
    }
}
