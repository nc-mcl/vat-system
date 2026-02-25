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
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for VAT filing period operations.
 *
 * <p>Base path: {@code /api/v1/periods}
 */
@RestController
@RequestMapping("/api/v1/periods")
@Tag(name = "Tax Periods", description = "Open and manage VAT filing periods")
public class TaxPeriodController {

    private final TaxPeriodRepository taxPeriodRepository;
    private final AuditLogger auditLogger;
    private final JurisdictionRegistry registry;

    public TaxPeriodController(
            TaxPeriodRepository taxPeriodRepository,
            AuditLogger auditLogger,
            JurisdictionRegistry registry) {
        this.taxPeriodRepository = taxPeriodRepository;
        this.auditLogger = auditLogger;
        this.registry = registry;
    }

    /**
     * Opens a new VAT filing period.
     *
     * @return 201 Created with the new period
     */
    @PostMapping
    @Operation(summary = "Open a new filing period")
    public ResponseEntity<TaxPeriodResponse> openPeriod(@Valid @RequestBody OpenPeriodRequest req) {
        JurisdictionCode code = JurisdictionCode.fromString(req.jurisdictionCode());
        JurisdictionPlugin plugin = registry.getPlugin(code);

        LocalDate start = LocalDate.parse(req.periodStart());
        LocalDate end   = LocalDate.parse(req.periodEnd());
        FilingCadence cadence = FilingCadence.valueOf(req.filingCadence().toUpperCase());

        TaxPeriod period = new TaxPeriod(UUID.randomUUID(), code, start, end, cadence, TaxPeriodStatus.OPEN);
        LocalDate filingDeadline = plugin.calculateFilingDeadline(period);

        // Audit first
        auditLogger.log(new AuditEvent(
                "TaxPeriod", period.id(), "PERIOD_OPENED", "system",
                Map.of(
                        "jurisdictionCode", code.name(),
                        "periodStart", start.toString(),
                        "periodEnd", end.toString(),
                        "filingCadence", cadence.name(),
                        "filingDeadline", filingDeadline.toString()
                ),
                code
        ));

        TaxPeriod saved = taxPeriodRepository.save(period, filingDeadline);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved, filingDeadline));
    }

    /**
     * Retrieves a filing period by ID.
     *
     * @return 200 OK with the period, or 404 Not Found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a filing period by ID")
    public ResponseEntity<TaxPeriodResponse> getPeriod(@PathVariable UUID id) {
        TaxPeriod period = taxPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Tax period not found: " + id));
        // Filing deadline is not stored in the domain record; recompute from plugin
        JurisdictionPlugin plugin = registry.getPlugin(period.jurisdictionCode());
        LocalDate filingDeadline = plugin.calculateFilingDeadline(period);
        return ResponseEntity.ok(toResponse(period, filingDeadline));
    }

    /**
     * Lists all filing periods for a jurisdiction.
     *
     * @return 200 OK with list of periods
     */
    @GetMapping
    @Operation(summary = "List filing periods for a jurisdiction")
    public ResponseEntity<List<TaxPeriodResponse>> listPeriods(
            @RequestParam String jurisdictionCode) {
        JurisdictionCode code = JurisdictionCode.fromString(jurisdictionCode);
        JurisdictionPlugin plugin = registry.getPlugin(code);
        List<TaxPeriodResponse> responses = taxPeriodRepository.findByJurisdiction(code).stream()
                .map(p -> toResponse(p, plugin.calculateFilingDeadline(p)))
                .toList();
        return ResponseEntity.ok(responses);
    }

    private TaxPeriodResponse toResponse(TaxPeriod period, LocalDate filingDeadline) {
        return new TaxPeriodResponse(
                period.id().toString(),
                period.jurisdictionCode().name(),
                period.startDate().toString(),
                period.endDate().toString(),
                period.cadence().name(),
                filingDeadline.toString(),
                period.status().name()
        );
    }
}
