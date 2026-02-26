package com.netcompany.vat.api.reporting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for VAT reporting endpoints.
 *
 * <p>Base path: {@code /api/v1/reporting}
 *
 * <p>Reporting is read-only — these endpoints generate report payloads from
 * persisted VAT returns without modifying any domain state.
 *
 * <h2>Phase 1: Available endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/reporting/returns/{id}/momsangivelse} — formatted DK momsangivelse</li>
 *   <li>{@code GET /api/v1/reporting/returns/{id}/payload} — full report payload with metadata</li>
 * </ul>
 *
 * <h2>Phase 2: Stubbed endpoints (return 501)</h2>
 * <ul>
 *   <li>{@code GET /api/v1/reporting/returns/{id}/saft} — SAF-T XML export</li>
 *   <li>{@code POST /api/v1/reporting/returns/{id}/drr} — ViDA DRR submission</li>
 *   <li>{@code GET /api/v1/reporting/returns/{id}/eu-salgsangivelse} — EU sales list</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reporting")
@Tag(name = "Reporting", description = "Generate VAT return reports and authority payloads")
public class ReportingController {

    private final VatReportingService reportingService;
    private final SaftReportingService saftService;
    private final ViDaDrrService drrService;

    public ReportingController(
            VatReportingService reportingService,
            SaftReportingService saftService,
            ViDaDrrService drrService) {
        this.reportingService = reportingService;
        this.saftService = saftService;
        this.drrService = drrService;
    }

    // =========================================================================
    // Phase 1 — available endpoints
    // =========================================================================

    /**
     * Returns the formatted Danish momsangivelse (VAT return) for the given return ID.
     *
     * <p>The response includes all individual rubrik fields, box values, and derived amounts.
     * This endpoint is the primary reporting output for SKAT momsangivelse submissions.
     *
     * @param id the VAT return UUID
     * @return 200 OK with the formatted momsangivelse, or 404 if not found
     */
    @GetMapping("/returns/{id}/momsangivelse")
    @Operation(summary = "Get formatted DK momsangivelse for a VAT return")
    public ResponseEntity<DkMomsangivelse> getMomsangivelse(@PathVariable UUID id) {
        DkMomsangivelse momsangivelse = reportingService.generateMomsangivelse(id);
        return ResponseEntity.ok(momsangivelse);
    }

    /**
     * Returns the full report payload (momsangivelse + metadata) for the given return ID.
     *
     * <p>Useful for integrations that need the payload envelope with format identifier
     * and generation timestamp.
     *
     * @param id the VAT return UUID
     * @return 200 OK with the report payload, or 404 if not found
     */
    @GetMapping("/returns/{id}/payload")
    @Operation(summary = "Get full report payload for a VAT return")
    public ResponseEntity<VatReportPayload> getPayload(@PathVariable UUID id) {
        VatReportPayload payload = reportingService.generatePayload(id);
        return ResponseEntity.ok(payload);
    }

    // =========================================================================
    // Phase 2 stubs — return 501 Not Implemented
    // =========================================================================

    /**
     * Phase 2 stub: SAF-T XML export.
     * Returns 501 Not Implemented until Phase 2 is delivered.
     *
     * @param id the VAT return UUID
     * @throws UnsupportedOperationException always — SAF-T export is Phase 2 scope
     */
    @GetMapping("/returns/{id}/saft")
    @Operation(summary = "[Phase 2] SAF-T XML export — returns 501 Not Implemented")
    public ResponseEntity<byte[]> getSaft(@PathVariable UUID id) {
        saftService.generateSaftXml(id);
        return ResponseEntity.ok().build(); // unreachable; exception thrown above
    }

    /**
     * Phase 2 stub: ViDA DRR real-time submission.
     * Returns 501 Not Implemented until Phase 2 is delivered.
     *
     * @param id the VAT return UUID
     * @throws UnsupportedOperationException always — DRR is Phase 2 scope
     */
    @PostMapping("/returns/{id}/drr")
    @Operation(summary = "[Phase 2] ViDA DRR submission — returns 501 Not Implemented")
    public ResponseEntity<Void> submitDrr(@PathVariable UUID id) {
        drrService.submitDrr(id);
        return ResponseEntity.accepted().build(); // unreachable; exception thrown above
    }

    /**
     * Phase 2 stub: EU-salgsangivelse (EC Sales List) generation.
     * Returns 501 Not Implemented until Phase 2 is delivered.
     *
     * @param id the VAT return UUID
     * @throws UnsupportedOperationException always — EU sales list is Phase 2 scope
     */
    @GetMapping("/returns/{id}/eu-salgsangivelse")
    @Operation(summary = "[Phase 2] EU-salgsangivelse (EC Sales List) — returns 501 Not Implemented")
    public ResponseEntity<Void> getEuSalgsangivelse(@PathVariable UUID id) {
        drrService.generateEuSalgsangivelse(id);
        return ResponseEntity.ok().build(); // unreachable; exception thrown above
    }
}
