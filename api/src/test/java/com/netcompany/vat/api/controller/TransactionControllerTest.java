package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.config.JurisdictionRegistry;
import com.netcompany.vat.api.dto.CreateTransactionRequest;
import com.netcompany.vat.api.dto.TransactionResponse;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.api.exception.InvalidPeriodStateException;
import com.netcompany.vat.domain.FilingCadence;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import com.netcompany.vat.persistence.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionControllerTest {

    @Mock private TaxPeriodRepository taxPeriodRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditLogger auditLogger;
    @Mock private JurisdictionRegistry registry;
    @Mock private JurisdictionPlugin plugin;

    private TransactionController controller;

    private final UUID periodId = UUID.randomUUID();
    private TaxPeriod openPeriod;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        controller = new TransactionController(
                taxPeriodRepository, transactionRepository, auditLogger, registry);

        openPeriod = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.OPEN);

        when(registry.getPlugin(JurisdictionCode.DK)).thenReturn(plugin);
        when(plugin.getVatRateInBasisPoints(TaxCode.STANDARD, LocalDate.of(2026, 1, 15)))
                .thenReturn(2500L);
        when(plugin.getVatRateInBasisPoints(TaxCode.ZERO_RATED, LocalDate.of(2026, 1, 15)))
                .thenReturn(0L);
    }

    @Test
    void createTransaction_happyPath_returns201() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));

        Transaction saved = new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 1, 15), "Test sale",
                MonetaryAmount.ofOere(100_000L),
                new TaxClassification(TaxCode.STANDARD, 2500L, false, LocalDate.of(2026, 1, 15)),
                Instant.now());
        when(transactionRepository.save(any())).thenReturn(saved);

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "STANDARD", 100_000L, "2026-01-15", "Test sale", null);

        ResponseEntity<TransactionResponse> response = controller.createTransaction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().taxCode()).isEqualTo("STANDARD");
        assertThat(response.getBody().amountExclVat()).isEqualTo(100_000L);
        assertThat(response.getBody().vatAmount()).isEqualTo(25_000L); // 25% of 100,000
        verify(auditLogger).log(any());
    }

    @Test
    void createTransaction_periodNotFound_throwsEntityNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(taxPeriodRepository.findById(unknownId)).thenReturn(Optional.empty());

        CreateTransactionRequest req = new CreateTransactionRequest(
                unknownId.toString(), "STANDARD", 100_000L, "2026-01-15", "Test", null);

        assertThrows(EntityNotFoundException.class, () -> controller.createTransaction(req));
    }

    @Test
    void createTransaction_periodFiled_throws409() {
        TaxPeriod filedPeriod = new TaxPeriod(periodId, JurisdictionCode.DK,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                FilingCadence.QUARTERLY, TaxPeriodStatus.FILED);
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(filedPeriod));

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "STANDARD", 100_000L, "2026-01-15", "Test", null);

        assertThrows(InvalidPeriodStateException.class, () -> controller.createTransaction(req));
    }

    @Test
    void createTransaction_zeroRated_returnsZeroVat() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));

        Transaction saved = new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 1, 15), "Export",
                MonetaryAmount.ofOere(200_000L),
                new TaxClassification(TaxCode.ZERO_RATED, 0L, false, LocalDate.of(2026, 1, 15)),
                Instant.now());
        when(transactionRepository.save(any())).thenReturn(saved);

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "ZERO_RATED", 200_000L, "2026-01-15", "Export", null);

        ResponseEntity<TransactionResponse> response = controller.createTransaction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().vatAmount()).isEqualTo(0L);
    }

    @Test
    void createTransaction_reverseCharge_returnsIsReverseChargeTrue() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));
        when(plugin.getVatRateInBasisPoints(TaxCode.REVERSE_CHARGE, LocalDate.of(2026, 1, 15)))
                .thenReturn(2500L);

        TaxClassification rcClass = new TaxClassification(
                TaxCode.REVERSE_CHARGE, 2500L, true, LocalDate.of(2026, 1, 15));
        // RC VAT: 800,000 * 25% = 200,000 øre
        Transaction saved = new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 1, 15), "Cross-border B2B service",
                MonetaryAmount.ofOere(800_000L), rcClass, Instant.now());
        when(transactionRepository.save(any())).thenReturn(saved);

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "REVERSE_CHARGE", 800_000L, "2026-01-15",
                "Cross-border B2B service", null);

        ResponseEntity<TransactionResponse> response = controller.createTransaction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().isReverseCharge()).isTrue();
        assertThat(response.getBody().vatAmount()).isEqualTo(200_000L); // 25% of 800,000
        assertThat(response.getBody().taxCode()).isEqualTo("REVERSE_CHARGE");
    }

    @Test
    void createTransaction_exempt_returnsZeroVatAndNotReverseCharge() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));
        when(plugin.getVatRateInBasisPoints(TaxCode.EXEMPT, LocalDate.of(2026, 1, 15)))
                .thenReturn(-1L);

        TaxClassification exemptClass = new TaxClassification(
                TaxCode.EXEMPT, -1L, false, LocalDate.of(2026, 1, 15));
        // Exempt: MOMS rate = -1 (not applicable) → vatAmount() returns ZERO
        Transaction saved = new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK, periodId, null,
                LocalDate.of(2026, 1, 15), "Medical service — ML §13",
                MonetaryAmount.ofOere(500_000L), exemptClass, Instant.now());
        when(transactionRepository.save(any())).thenReturn(saved);

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "EXEMPT", 500_000L, "2026-01-15",
                "Medical service", null);

        ResponseEntity<TransactionResponse> response = controller.createTransaction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().vatAmount()).isEqualTo(0L);
        assertThat(response.getBody().isReverseCharge()).isFalse();
        assertThat(response.getBody().taxCode()).isEqualTo("EXEMPT");
    }

    @Test
    void createTransaction_withCounterpartyId_persistsCounterpartyReference() {
        when(taxPeriodRepository.findById(periodId)).thenReturn(Optional.of(openPeriod));

        UUID counterpartyId = UUID.randomUUID();
        TaxClassification cls = new TaxClassification(
                TaxCode.STANDARD, 2500L, false, LocalDate.of(2026, 1, 15));
        Transaction saved = new Transaction(
                UUID.randomUUID(), JurisdictionCode.DK, periodId, counterpartyId,
                LocalDate.of(2026, 1, 15), "Sale to known counterparty",
                MonetaryAmount.ofOere(100_000L), cls, Instant.now());
        when(transactionRepository.save(any())).thenReturn(saved);

        CreateTransactionRequest req = new CreateTransactionRequest(
                periodId.toString(), "STANDARD", 100_000L, "2026-01-15",
                "Sale to known counterparty", counterpartyId.toString());

        ResponseEntity<TransactionResponse> response = controller.createTransaction(req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().counterpartyId()).isEqualTo(counterpartyId.toString());
    }
}
