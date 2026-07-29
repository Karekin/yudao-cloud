package cn.iocoder.yudao.module.cloudmold.crossborder.service.query;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCaseView;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderQueryApi;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.ComplianceAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.CrossBorderCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrossBorderQueryService implements CrossBorderQueryApi {
    private final CrossBorderCaseMapper mapper;

    @Override
    public CrossBorderCaseView get(String caseId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CaseRecord row = mapper.selectCase(tenantId, caseId);
        if (row == null) {
            throw new IllegalArgumentException("cross-border case not found");
        }
        ComplianceAssessmentRecord assessment = row.getAssessmentId() == null ? null
                : mapper.selectAssessment(tenantId, row.getAssessmentId());
        CrossBorderCaseView view = toView(row, assessment, mapper.selectHistory(tenantId, caseId));
        if (!List.of("DELIVERED", "CLOSED").contains(view.getStatus())) {
            throw new IllegalArgumentException("cross-border case is not terminal");
        }
        return view;
    }

    private CrossBorderCaseView toView(CaseRecord row, ComplianceAssessmentRecord assessment,
                                       List<StatusHistoryRecord> history) {
        return CrossBorderCaseView.builder()
                .caseId(row.getCaseId())
                .caseNo(row.getCaseNo())
                .orderId(row.getOrderId())
                .fulfillmentId(row.getFulfillmentId())
                .tradeMode(row.getTradeMode())
                .originCountry(row.getOriginCountry())
                .destinationCountry(row.getDestinationCountry())
                .status(row.getStatus())
                .customsStatus(row.getCustomsReleaseRef() == null ? "PENDING" : "RELEASED")
                .deliveryStatus(row.getDeliveryEvidenceRef() == null ? "PENDING" : "DELIVERED")
                .version(row.getVersion())
                .approvalRef(row.getApprovalRef())
                .handoverRef(row.getHandoverRef())
                .customsDeclarationRef(row.getCustomsDeclarationRef())
                .customsReleaseRef(row.getCustomsReleaseRef())
                .deliveryEvidenceRef(row.getDeliveryEvidenceRef())
                .closeReason(row.getCloseReason())
                .assessment(assessment == null ? null : CrossBorderCaseView.ComplianceAssessmentView.builder()
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
                .route(row.getRouteCode() == null ? null : CrossBorderCaseView.RouteView.builder()
                        .routeCode(row.getRouteCode())
                        .carrierCode(row.getRouteCarrierCode())
                        .serviceLevel(row.getRouteServiceLevel())
                        .slaDays(row.getRouteSlaDays())
                        .build())
                .declaration(row.getDeclarationId() == null ? null : CrossBorderCaseView.DeclarationView.builder()
                        .declarationId(row.getDeclarationId())
                        .hsCode(row.getHsCode())
                        .goodsDescription(row.getGoodsDescription())
                        .quantity(row.getQuantity())
                        .declaredAmountMinor(row.getDeclaredAmountMinor())
                        .currency(row.getCurrency())
                        .evidenceRef(row.getDeclarationEvidenceRef())
                        .build())
                .documents(row.getDocumentOrderRef() == null ? null : CrossBorderCaseView.DocumentsView.builder()
                        .orderRef(row.getDocumentOrderRef())
                        .paymentRef(row.getDocumentPaymentRef())
                        .logisticsRef(row.getDocumentLogisticsRef())
                        .validationEvidenceRef(row.getDocumentValidationEvidenceRef())
                        .build())
                .booking(row.getBookingRef() == null ? null : CrossBorderCaseView.BookingView.builder()
                        .bookingRef(row.getBookingRef())
                        .carrierCode(row.getBookingCarrierCode())
                        .serviceLevel(row.getBookingServiceLevel())
                        .build())
                .label(row.getLabelRef() == null ? null : CrossBorderCaseView.LabelView.builder()
                        .labelRef(row.getLabelRef())
                        .trackingNumber(row.getTrackingNumber())
                        .build())
                .history(history.stream().map(item -> CrossBorderCaseView.HistoryView.builder()
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
