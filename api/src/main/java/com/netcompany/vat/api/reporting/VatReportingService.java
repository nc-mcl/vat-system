package com.netcompany.vat.api.reporting;

import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.persistence.repository.TaxPeriodRepository;
import com.netcompany.vat.persistence.repository.VatReturnRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates VAT report generation from persisted data.
 *
 * <p>Reporting is read-only: this service never modifies domain state.
 * All report generation derives from an already-assembled {@link VatReturn}
 * and its associated {@link TaxPeriod}.
 *
 * <p>Phase 1: DK momsangivelse JSON payload only.
 * Phase 2: SAF-T export, ViDA DRR submission (see {@link SaftReportingService}
 * and {@link ViDaDrrService}).
 */
@Service
public class VatReportingService {

    static final String FORMAT_DK_MOMSANGIVELSE_JSON = "DK_MOMSANGIVELSE_JSON";

    private final VatReturnRepository vatReturnRepository;
    private final TaxPeriodRepository taxPeriodRepository;
    private final DkVatReturnFormatter dkFormatter;

    public VatReportingService(
            VatReturnRepository vatReturnRepository,
            TaxPeriodRepository taxPeriodRepository,
            DkVatReturnFormatter dkFormatter) {
        this.vatReturnRepository = vatReturnRepository;
        this.taxPeriodRepository = taxPeriodRepository;
        this.dkFormatter = dkFormatter;
    }

    /**
     * Formats a persisted VAT return as a {@link DkMomsangivelse}.
     *
     * @param returnId the VAT return UUID
     * @return the formatted momsangivelse
     * @throws EntityNotFoundException if the return or its period is not found
     * @throws IllegalArgumentException if the return is not for jurisdiction DK
     */
    public DkMomsangivelse generateMomsangivelse(UUID returnId) {
        VatReturn vatReturn = fetchReturn(returnId);
        requireDkJurisdiction(vatReturn);
        TaxPeriod period = fetchPeriod(vatReturn.periodId());
        return dkFormatter.format(vatReturn, period);
    }

    /**
     * Generates a full report payload wrapping the formatted momsangivelse.
     *
     * @param returnId the VAT return UUID
     * @return the report payload with metadata
     * @throws EntityNotFoundException if the return or its period is not found
     * @throws IllegalArgumentException if the return is not for jurisdiction DK
     */
    public VatReportPayload generatePayload(UUID returnId) {
        DkMomsangivelse momsangivelse = generateMomsangivelse(returnId);
        return new VatReportPayload(
                returnId,
                Instant.now(),
                FORMAT_DK_MOMSANGIVELSE_JSON,
                momsangivelse
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private VatReturn fetchReturn(UUID returnId) {
        return vatReturnRepository.findById(returnId)
                .orElseThrow(() -> new EntityNotFoundException("VAT return not found: " + returnId));
    }

    private TaxPeriod fetchPeriod(UUID periodId) {
        return taxPeriodRepository.findById(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Tax period not found: " + periodId));
    }

    private static void requireDkJurisdiction(VatReturn vatReturn) {
        if (vatReturn.jurisdictionCode() != JurisdictionCode.DK) {
            throw new IllegalArgumentException(
                    "DK momsangivelse report is only available for jurisdiction DK; " +
                    "got: " + vatReturn.jurisdictionCode());
        }
    }
}
