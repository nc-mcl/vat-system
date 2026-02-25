package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.config.JurisdictionRegistry;
import com.netcompany.vat.api.dto.CreateTransactionRequest;
import com.netcompany.vat.api.dto.TransactionResponse;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.api.exception.InvalidPeriodStateException;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.MonetaryAmount;
import com.netcompany.vat.domain.TaxClassification;
import com.netcompany.vat.domain.TaxCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import com.netcompany.vat.persistence.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for VAT transaction operations.
 *
 * <p>Base path: {@code /api/v1/transactions}
 *
 * <p><strong>All monetary amounts are in øre (1 DKK = 100 øre).</strong>
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Submit and query VAT transactions")
public class TransactionController {

    private final TaxPeriodRepository taxPeriodRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogger auditLogger;
    private final JurisdictionRegistry registry;

    public TransactionController(
            TaxPeriodRepository taxPeriodRepository,
            TransactionRepository transactionRepository,
            AuditLogger auditLogger,
            JurisdictionRegistry registry) {
        this.taxPeriodRepository = taxPeriodRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogger = auditLogger;
        this.registry = registry;
    }

    /**
     * Submits a new VAT transaction to an open period.
     *
     * @return 201 Created with the saved transaction
     */
    @PostMapping
    @Operation(summary = "Submit a VAT transaction")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest req) {

        UUID periodId = UUID.fromString(req.periodId());
        TaxPeriod period = taxPeriodRepository.findById(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Tax period not found: " + periodId));

        if (!period.isOpen()) {
            throw new InvalidPeriodStateException(
                    "Tax period " + periodId + " is not OPEN — current status: " + period.status());
        }

        JurisdictionPlugin plugin = registry.getPlugin(period.jurisdictionCode());
        TaxCode taxCode = TaxCode.valueOf(req.taxCode().toUpperCase());
        LocalDate transactionDate = LocalDate.parse(req.transactionDate());

        // Resolve the authoritative VAT rate from the jurisdiction plugin
        long rateInBasisPoints = plugin.getVatRateInBasisPoints(taxCode, transactionDate);
        boolean isReverseCharge = (taxCode == TaxCode.REVERSE_CHARGE);

        TaxClassification classification = new TaxClassification(
                taxCode, rateInBasisPoints, isReverseCharge, transactionDate);

        UUID counterpartyId = req.counterpartyId() != null && !req.counterpartyId().isBlank()
                ? UUID.fromString(req.counterpartyId())
                : null;

        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                period.jurisdictionCode(),
                periodId,
                counterpartyId,
                transactionDate,
                req.description(),
                MonetaryAmount.ofOere(req.amountExclVat()),
                classification,
                Instant.now()
        );

        // Audit first
        auditLogger.log(new AuditEvent(
                "Transaction", transaction.id(), "TRANSACTION_CREATED", "system",
                Map.of(
                        "periodId", periodId.toString(),
                        "taxCode", taxCode.name(),
                        "amountExclVat", req.amountExclVat(),
                        "vatAmount", transaction.vatAmount().oere(),
                        "transactionDate", transactionDate.toString()
                ),
                period.jurisdictionCode()
        ));

        Transaction saved = transactionRepository.save(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Lists all transactions for a filing period.
     *
     * @return 200 OK with list of transactions
     */
    @GetMapping
    @Operation(summary = "List transactions for a period")
    public ResponseEntity<List<TransactionResponse>> listTransactions(@RequestParam UUID periodId) {
        List<TransactionResponse> responses = transactionRepository.findByPeriod(periodId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.id().toString(),
                tx.periodId().toString(),
                tx.classification().taxCode().name(),
                tx.classification().taxCode().name() + " @ " +
                        String.format("%.2f%%", tx.classification().rateAsDecimal() * 100),
                tx.amountExclVat().oere(),
                tx.vatAmount().oere(),
                tx.classification().rateInBasisPoints(),
                tx.transactionDate().toString(),
                tx.description(),
                tx.classification().isReverseCharge(),
                tx.counterpartyId() != null ? tx.counterpartyId().toString() : null
        );
    }
}
