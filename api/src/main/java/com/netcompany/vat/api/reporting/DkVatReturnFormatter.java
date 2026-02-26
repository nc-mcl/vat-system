package com.netcompany.vat.api.reporting;

import com.netcompany.vat.domain.TaxPeriod;
import com.netcompany.vat.domain.VatReturn;
import com.netcompany.vat.taxengine.VatReturnAssembler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Formats a persisted {@link VatReturn} into a {@link DkMomsangivelse} payload
 * ready for display, audit, or authority submission.
 *
 * <p>This formatter is deterministic and stateless — the same input always produces
 * the same output. It does not call external services or modify any domain state.
 *
 * <h2>Rubrik routing (Phase 1 approximations)</h2>
 * The assembler has already routed all amounts into the {@code jurisdictionFields} map.
 * This formatter extracts those values and derives any cross-field constraints:
 *
 * <ul>
 *   <li>Box 4 ({@code vatOnServicesPurchasesAbroadAmount}) is a <em>component</em> of Box 1
 *       ({@code outputVatAmount}). The assembler already includes it in output VAT total.</li>
 *   <li>Box 3 ({@code vatOnGoodsPurchasesAbroadAmount}) is also a component of Box 1.
 *       Phase 1: EU goods purchase VAT is not separately tracked (goods split is Phase 2),
 *       so this field will be 0 until {@code transactionType} is added to Transaction.</li>
 * </ul>
 *
 * <p>See {@link DkMomsangivelse} for the Phase 1 limitation note.
 */
@Component
public class DkVatReturnFormatter {

    static final String PHASE_1_LIMITATION =
            "Phase 1 approximation: REVERSE_CHARGE transactions are routed to Rubrik A services " +
            "(EU services assumed); ZERO_RATED transactions are routed to Rubrik B services " +
            "(EU services assumed). Accurate goods/services and EU/non-EU split requires " +
            "Transaction.transactionType and Transaction.counterpartyJurisdiction fields (Phase 2). " +
            "Non-EU service net value treatment is MEDIUM confidence per expert-review-answers-rubrik.md.";

    /**
     * Formats a {@link VatReturn} and its associated {@link TaxPeriod} into a {@link DkMomsangivelse}.
     *
     * @param vatReturn the assembled (or accepted) VAT return
     * @param period    the tax period the return covers, used for period dates
     * @return the formatted momsangivelse
     */
    public DkMomsangivelse format(VatReturn vatReturn, TaxPeriod period) {
        Map<String, Object> fields = vatReturn.jurisdictionFields();

        long rubrikAGoods    = toLong(fields.get(VatReturnAssembler.FIELD_RUBRIK_A_GOODS));
        long rubrikAServices = toLong(fields.get(VatReturnAssembler.FIELD_RUBRIK_A_SERVICES));
        long rubrikBGoods    = toLong(fields.get(VatReturnAssembler.FIELD_RUBRIK_B_GOODS));
        long rubrikBServices = toLong(fields.get(VatReturnAssembler.FIELD_RUBRIK_B_SERVICES));
        long rubrikCOther    = toLong(fields.get(VatReturnAssembler.FIELD_RUBRIK_C_OTHER));

        // Box 3: acquisition VAT on EU goods purchases. Phase 1: not separately tracked (0).
        long vatOnGoodsAbroad = toLong(fields.get(VatReturnAssembler.FIELD_VAT_ON_GOODS_ABROAD));

        // Box 4: reverse-charge VAT on foreign services (EU + non-EU), component of Box 1.
        // HIGH confidence per expert-review-answers-rubrik.md G3-Q4.
        long vatOnSvcAbroad = toLong(fields.get(VatReturnAssembler.FIELD_VAT_ON_SVC_ABROAD));

        return new DkMomsangivelse(
                vatReturn.id(),
                vatReturn.periodId(),
                vatReturn.jurisdictionCode().name(),
                period.startDate(),
                period.endDate(),
                vatReturn.outputVat().oere(),
                vatReturn.inputVatDeductible().oere(),
                vatOnGoodsAbroad,
                vatOnSvcAbroad,
                rubrikAGoods,
                rubrikAServices,
                rubrikBGoods,
                rubrikBServices,
                rubrikCOther,
                vatReturn.netVat().oere(),
                vatReturn.resultType().name(),
                vatReturn.claimAmount().oere(),
                vatReturn.status().name(),
                PHASE_1_LIMITATION
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
