package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.config.JurisdictionRegistry;
import com.netcompany.vat.api.dto.AssembleReturnRequest;
import com.netcompany.vat.api.dto.VatReturnResponse;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.api.exception.InvalidPeriodStateException;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.Result;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.TaxPeriodStatus;
import com.netcompany.vat.domain.Transaction;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.domain.VatReturnStatus;
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import com.netcompany.vat.persistence.repository.TransactionRepository;
import com.netcompany.vat.persistence.repository.VatReturnRepository;
import com.netcompany.vat.taxengine.TaxEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for VAT return operations.
 *
 * <p>Base path: {@code /api/v1/returns}
 *
 * <p>Lifecycle: DRAFT → SUBMITTED (→ ACCEPTED / REJECTED by Integration Agent).
 */
@RestController
@RequestMapping("/api/v1/returns")
@Tag(name = "VAT Returns", description = "Assemble, submit, and query VAT returns")
public class VatReturnController {

    private final TaxPeriodRepository taxPeriodRepository;
    private final TransactionRepository transactionRepository;
    private final VatReturnRepository vatReturnRepository;
    private final AuditLogger auditLogger;
    private final JurisdictionRegistry registry;

    public VatReturnController(
            TaxPeriodRepository taxPeriodRepository,
            TransactionRepository transactionRepository,
            VatReturnRepository vatReturnRepository,
            AuditLogger auditLogger,
            JurisdictionRegistry registry) {
        this.taxPeriodRepository = taxPeriodRepository;
        this.transactionRepository = transactionRepository;
        this.vatReturnRepository = vatReturnRepository;
        this.auditLogger = auditLogger;
        this.registry = registry;
    }

    /**
     * Assembles a VAT return for a period from its transactions.
     *
     * @return 201 Created with the assembled DRAFT return
     */
    @PostMapping("/assemble")
    @Operation(summary = "Assemble a VAT return for a period")
    public ResponseEntity<VatReturnResponse> assembleReturn(
            @Valid @RequestBody AssembleReturnRequest req) {

        UUID periodId = UUID.fromString(req.periodId());
        JurisdictionCode code = JurisdictionCode.fromString(req.jurisdictionCode());

        TaxPeriod period = taxPeriodRepository.findById(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Tax period not found: " + periodId));

        // Guard: only one return per period+jurisdiction
        if (vatReturnRepository.findByPeriod(periodId, code).isPresent()) {
            throw new InvalidPeriodStateException(
                    "A VAT return already exists for period " + periodId + " in jurisdiction " + code);
        }

        List<Transaction> transactions = transactionRepository.findByPeriod(periodId);

        TaxEngine engine = registry.getTaxEngine(code);
        Result<VatReturn> assembleResult = engine.assembleReturn(transactions, period);

        if (assembleResult instanceof Result.Err<VatReturn> err) {
            throw new InvalidPeriodStateException(
                    "Cannot assemble return: period is not OPEN — " + err.error());
        }
        VatReturn assembled = ((Result.Ok<VatReturn>) assembleResult).value();

        // Audit first
        auditLogger.log(new AuditEvent(
                "VatReturn", assembled.id(), "RETURN_ASSEMBLED", "system",
                Map.of(
                        "periodId", periodId.toString(),
                        "jurisdictionCode", code.name(),
                        "outputVat", assembled.outputVat().oere(),
                        "inputVat", assembled.inputVatDeductible().oere(),
                        "netVat", assembled.netVat().oere(),
                        "resultType", assembled.resultType().name()
                ),
                code
        ));

        VatReturn saved = vatReturnRepository.save(assembled);

        // Lock the period — no more transactions can be added
        taxPeriodRepository.updateStatus(periodId, TaxPeriodStatus.FILED);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Submits a DRAFT VAT return.
     *
     * <p><strong>Note:</strong> SKAT integration is not yet implemented.
     * This endpoint transitions the return to SUBMITTED status and returns 202 Accepted.
     * The Integration Agent will replace this stub with a real SKAT API call.
     *
     * @return 202 Accepted
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a VAT return to SKAT (stub — Integration Agent pending)")
    public ResponseEntity<Map<String, Object>> submitReturn(@PathVariable UUID id) {
        VatReturn vatReturn = vatReturnRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("VAT return not found: " + id));

        if (vatReturn.status() != VatReturnStatus.DRAFT) {
            throw new InvalidPeriodStateException(
                    "VAT return " + id + " cannot be submitted — current status: " + vatReturn.status());
        }

        // Audit first
        auditLogger.log(new AuditEvent(
                "VatReturn", id, "RETURN_SUBMITTED", "system",
                Map.of(
                        "returnId", id.toString(),
                        "previousStatus", VatReturnStatus.DRAFT.name(),
                        "newStatus", VatReturnStatus.SUBMITTED.name()
                ),
                vatReturn.jurisdictionCode()
        ));

        vatReturnRepository.updateStatus(id, VatReturnStatus.SUBMITTED, Instant.now());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "id", id.toString(),
                        "status", VatReturnStatus.SUBMITTED.name(),
                        "note", "SKAT filing is pending the Integration Agent. " +
                                "The return has been queued for submission."
                ));
    }

    /**
     * Retrieves a VAT return by ID.
     *
     * @return 200 OK with the return, or 404 Not Found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a VAT return by ID")
    public ResponseEntity<VatReturnResponse> getReturn(@PathVariable UUID id) {
        VatReturn vatReturn = vatReturnRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("VAT return not found: " + id));
        return ResponseEntity.ok(toResponse(vatReturn));
    }

    /**
     * Gets the VAT return for a period.
     *
     * @return 200 OK with the return, or empty list if none
     */
    @GetMapping
    @Operation(summary = "Get VAT return for a period")
    public ResponseEntity<List<VatReturnResponse>> getReturnForPeriod(
            @RequestParam UUID periodId,
            @RequestParam(required = false, defaultValue = "DK") String jurisdictionCode) {
        JurisdictionCode code = JurisdictionCode.fromString(jurisdictionCode);
        return vatReturnRepository.findByPeriod(periodId, code)
                .map(r -> ResponseEntity.ok(List.of(toResponse(r))))
                .orElse(ResponseEntity.ok(List.of()));
    }

    private VatReturnResponse toResponse(VatReturn r) {
        Map<String, Object> fields = r.jurisdictionFields();

        long rubrikA = toLong(fields.get("rubrikAGoodsEuPurchaseValue"))
                + toLong(fields.get("rubrikAServicesEuPurchaseValue"));
        long rubrikB = toLong(fields.get("rubrikBGoodsEuSaleValue"))
                + toLong(fields.get("rubrikBServicesEuSaleValue"));

        return new VatReturnResponse(
                r.id().toString(),
                r.periodId().toString(),
                r.jurisdictionCode().name(),
                r.outputVat().oere(),
                r.inputVatDeductible().oere(),
                r.netVat().oere(),
                r.resultType().name(),
                rubrikA,
                rubrikB,
                r.status().name(),
                null,   // assembledAt — not stored in domain record; Integration Agent may populate
                null,   // submittedAt
                null,   // acceptedAt
                null    // skatReference
        );
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
