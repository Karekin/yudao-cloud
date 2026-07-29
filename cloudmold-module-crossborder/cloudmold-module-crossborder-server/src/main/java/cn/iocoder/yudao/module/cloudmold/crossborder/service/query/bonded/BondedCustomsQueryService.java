package cn.iocoder.yudao.module.cloudmold.crossborder.service.query.bonded;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsCaseView;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.BondedCustomsQueryApi;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.EligibilityAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.bonded.BondedCustomsCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BondedCustomsQueryService implements BondedCustomsQueryApi {
    private final BondedCustomsCaseMapper mapper;

    @Override
    public BondedCustomsCaseView get(String caseId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CaseRecord row = mapper.selectCase(tenantId, caseId);
        if (row == null) {
            throw new IllegalArgumentException("bonded customs case not found");
        }
        if (!"CLOSED".equals(row.getStatus())
                || !"TRIPLE_MATCHED".equals(row.getTripleMatchStatus())
                || !"CUSTOMS_ACCEPTED".equals(row.getCustomsStatus())
                || !"BONDED_RELEASED".equals(row.getBondedReleaseStatus())
                || !"DELIVERED".equals(row.getDeliveryStatus())) {
            throw new IllegalArgumentException("bonded customs case is not terminal");
        }
        EligibilityAssessmentRecord assessment = row.getAssessmentId() == null ? null
                : mapper.selectAssessment(tenantId, row.getAssessmentId());
        return toView(row, assessment, mapper.selectHistory(tenantId, caseId));
    }

    private BondedCustomsCaseView toView(CaseRecord row, EligibilityAssessmentRecord assessment,
                                         List<StatusHistoryRecord> history) {
        return BondedCustomsCaseView.builder()
                .caseId(row.getCaseId())
                .caseNo(row.getCaseNo())
                .mode(row.getMode())
                .canonicalOrderId(row.getCanonicalOrderId())
                .status(row.getStatus())
                .tripleMatchStatus(row.getTripleMatchStatus())
                .customsStatus(row.getCustomsStatus())
                .bondedReleaseStatus(row.getBondedReleaseStatus())
                .deliveryStatus(row.getDeliveryStatus())
                .version(row.getVersion())
                .approvalRef(row.getApprovalRef())
                .declarationRef(row.getDeclarationRef())
                .customsAcceptanceRef(row.getCustomsAcceptanceRef())
                .bondedReleaseRef(row.getBondedReleaseRef())
                .deliveryConfirmationRef(row.getDeliveryConfirmationRef())
                .closeReason(row.getCloseReason())
                .tripleOrder(BondedCustomsCaseView.TripleOrderView.builder()
                        .orderRef(row.getOrderRef())
                        .paymentRef(row.getPaymentRef())
                        .logisticsRef(row.getLogisticsRef())
                        .orderAmountMinor(row.getOrderAmountMinor())
                        .paymentAmountMinor(row.getPaymentAmountMinor())
                        .logisticsAmountMinor(row.getLogisticsAmountMinor())
                        .currency(row.getCurrency())
                        .buyerIdentityHash(row.getBuyerIdentityHash())
                        .receiverIdentityHash(row.getReceiverIdentityHash())
                        .declarantIdentityHash(row.getDeclarantIdentityHash())
                        .orderSnapshotRef(row.getOrderSnapshotRef())
                        .paymentSnapshotRef(row.getPaymentSnapshotRef())
                        .logisticsSnapshotRef(row.getLogisticsSnapshotRef())
                        .build())
                .eligibilityAssessment(assessment == null ? null : BondedCustomsCaseView.EligibilityAssessmentView.builder()
                        .facts(parseStringList(assessment.getFactsJson()))
                        .options(parseStringList(assessment.getOptionsJson()))
                        .recommendation(assessment.getRecommendation())
                        .risks(parseStringList(assessment.getRisksJson()))
                        .confidence(assessment.getConfidence())
                        .missingFacts(parseStringList(assessment.getMissingFactsJson()))
                        .evidenceRef(assessment.getEvidenceRef())
                        .assessedAt(assessment.getAssessedAt().toInstant(ZoneOffset.UTC))
                        .assessedByPrincipalId(assessment.getAssessedByPrincipalId())
                        .build())
                .goodsClassification(BondedCustomsCaseView.GoodsClassificationView.builder()
                        .hsCode(row.getHsCode())
                        .positiveListCode(row.getPositiveListCode())
                        .goodsName(row.getGoodsName())
                        .evidenceRef(row.getGoodsEvidenceRef())
                        .build())
                .taxCalculation(BondedCustomsCaseView.TaxCalculationView.builder()
                        .dutiableAmountMinor(row.getDutiableAmountMinor())
                        .consumptionTaxMinor(row.getConsumptionTaxMinor())
                        .valueAddedTaxMinor(row.getValueAddedTaxMinor())
                        .totalTaxMinor(row.getTotalTaxMinor())
                        .currency(row.getTaxCurrency())
                        .evidenceRef(row.getTaxEvidenceRef())
                        .build())
                .history(history.stream().map(item -> BondedCustomsCaseView.HistoryView.builder()
                        .aggregateVersion(item.getAggregateVersion())
                        .operation(item.getOperation())
                        .previousStatus(item.getPreviousStatus())
                        .currentStatus(item.getCurrentStatus())
                        .actorPrincipalId(item.getActorPrincipalId())
                        .occurredAt(item.getOccurredAt().toInstant(ZoneOffset.UTC))
                        .recordedAt(item.getCreatedAt().toInstant(ZoneOffset.UTC))
                        .detailJson(item.getDetailJson())
                        .build()).toList())
                .build();
    }

    private static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<?> raw = JsonUtils.parseArray(json, Object.class);
        return raw == null ? List.of() : raw.stream().map(String::valueOf).toList();
    }
}
