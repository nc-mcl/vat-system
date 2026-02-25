package com.netcompany.vat.api.controller;

import com.netcompany.vat.api.dto.CounterpartyResponse;
import com.netcompany.vat.api.dto.CreateCounterpartyRequest;
import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.domain.Counterparty;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.persistence.audit.AuditEvent;
import com.netcompany.vat.persistence.audit.AuditLogger;
import com.netcompany.vat.persistence.repository.CounterpartyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for counterparty (supplier / customer) operations.
 *
 * <p>Base path: {@code /api/v1/counterparties}
 */
@RestController
@RequestMapping("/api/v1/counterparties")
@Tag(name = "Counterparties", description = "Register and retrieve trading partners")
public class CounterpartyController {

    private final CounterpartyRepository counterpartyRepository;
    private final AuditLogger auditLogger;

    public CounterpartyController(
            CounterpartyRepository counterpartyRepository,
            AuditLogger auditLogger) {
        this.counterpartyRepository = counterpartyRepository;
        this.auditLogger = auditLogger;
    }

    /**
     * Registers a new counterparty.
     *
     * @return 201 Created with the new counterparty
     */
    @PostMapping
    @Operation(summary = "Register a counterparty")
    public ResponseEntity<CounterpartyResponse> createCounterparty(
            @Valid @RequestBody CreateCounterpartyRequest req) {

        JurisdictionCode code = JurisdictionCode.fromString(req.countryCode());
        Counterparty counterparty = new Counterparty(
                UUID.randomUUID(),
                code,
                req.vatNumber(),
                req.name()
        );

        // Audit first
        auditLogger.log(new AuditEvent(
                "Counterparty", counterparty.id(), "COUNTERPARTY_CREATED", "system",
                Map.of(
                        "name", req.name(),
                        "vatNumber", req.vatNumber() != null ? req.vatNumber() : "",
                        "countryCode", req.countryCode()
                ),
                code
        ));

        Counterparty saved = counterpartyRepository.save(counterparty);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /**
     * Retrieves a counterparty by ID.
     *
     * @return 200 OK with the counterparty, or 404 Not Found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a counterparty by ID")
    public ResponseEntity<CounterpartyResponse> getCounterparty(@PathVariable UUID id) {
        Counterparty counterparty = counterpartyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Counterparty not found: " + id));
        return ResponseEntity.ok(toResponse(counterparty));
    }

    private CounterpartyResponse toResponse(Counterparty c) {
        return new CounterpartyResponse(
                c.id().toString(),
                c.name(),
                c.vatNumber(),
                c.jurisdictionCode().name()
        );
    }
}
