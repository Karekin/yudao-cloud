package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionQueryApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionView;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.ProcurementReceiptInspectionRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.ProcurementReceiptInspectionMapper;
import cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo.ProcurementReceiptInspectionPageReqVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcurementReceiptInspectionQueryService implements ProcurementReceiptInspectionQueryApi {
    private final ProcurementReceiptInspectionMapper mapper;

    public PageResult<ProcurementReceiptInspectionPageItem> getPage(
            ProcurementReceiptInspectionPageReqVO request) {
        if (request == null) {
            throw new IllegalArgumentException("page request is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String status = text(request.getStatus());
        if (status != null) status = status.toUpperCase(Locale.ROOT);
        String receiptId = text(request.getReceiptId());
        String purchaseOrderId = text(request.getPurchaseOrderId());
        String supplierId = text(request.getSupplierId());
        String ownerId = text(request.getOwnerId());
        long total = mapper.countInspections(tenantId, status, receiptId, purchaseOrderId, supplierId, ownerId);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<ProcurementReceiptInspectionPageItem> list = safe(mapper.selectInspections(
                        tenantId, status, receiptId, purchaseOrderId, supplierId, ownerId,
                        offset, request.getPageSize())).stream()
                .map(ProcurementReceiptInspectionQueryService::pageItem).toList();
        return new PageResult<>(list, total);
    }

    @Override
    public ProcurementReceiptInspectionView get(String inspectionId) {
        if (!StringUtils.hasText(inspectionId)) {
            throw new IllegalArgumentException("inspectionId is required");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Inspection inspection = mapper.selectInspection(tenantId, inspectionId.trim());
        if (inspection == null) {
            throw new IllegalArgumentException("procurement receipt inspection not found");
        }
        List<InspectionLine> lineRows = safe(mapper.selectLines(tenantId, inspectionId.trim()));
        Map<String, List<InspectionSplit>> splitsByLine = safe(mapper.selectSplits(tenantId, inspectionId.trim()))
                .stream().collect(Collectors.groupingBy(InspectionSplit::getInspectionLineId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, List<Defect>> defectsByResult = safe(mapper.selectDefects(tenantId, inspectionId.trim()))
                .stream().collect(Collectors.groupingBy(Defect::getResultSplitId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ResultSplit>> resultsByBatch = safe(mapper.selectResultSplits(
                        tenantId, inspectionId.trim())).stream()
                .collect(Collectors.groupingBy(ResultSplit::getResultBatchId,
                        LinkedHashMap::new, Collectors.toList()));
        List<ProcurementReceiptInspectionView.LineView> lines = lineRows.stream()
                .map(line -> lineView(line, splitsByLine.getOrDefault(line.getInspectionLineId(), List.of())))
                .toList();
        List<ProcurementReceiptInspectionView.ResultBatchView> batches = safe(mapper.selectResultBatches(
                        tenantId, inspectionId.trim())).stream()
                .map(batch -> batchView(batch, resultsByBatch.getOrDefault(batch.getResultBatchId(), List.of()),
                        defectsByResult))
                .toList();
        return ProcurementReceiptInspectionView.builder()
                .inspectionId(inspection.getInspectionId()).inspectionCode(inspection.getInspectionCode())
                .receiptId(inspection.getReceiptId()).purchaseOrderId(inspection.getPurchaseOrderId())
                .supplierId(inspection.getSupplierId()).ownerType(inspection.getOwnerType())
                .ownerId(inspection.getOwnerId()).businessNo(inspection.getBusinessNo())
                .standardId(inspection.getStandardId()).standardVersion(inspection.getStandardVersion())
                .standardVersionId(inspection.getStandardVersionId())
                .standardContentSha256(inspection.getStandardContentSha256())
                .createdByPrincipalId(inspection.getCreatedByPrincipalId())
                .lastDecisionActorPrincipalId(inspection.getLastDecisionActorPrincipalId())
                .completedByPrincipalId(inspection.getCompletedByPrincipalId())
                .status(inspection.getStatus()).finalDecision(inspection.getFinalDecision())
                .receivedQuantity(inspection.getReceivedQuantity()).sampledQuantity(inspection.getSampledQuantity())
                .acceptedQuantity(inspection.getAcceptedQuantity()).rejectedQuantity(inspection.getRejectedQuantity())
                .quarantinedQuantity(inspection.getQuarantinedQuantity()).version(inspection.getVersion())
                .completedAt(inspection.getCompletedAt() == null ? null
                        : inspection.getCompletedAt().toInstant(ZoneOffset.UTC))
                .lines(lines).resultBatches(batches).build();
    }

    private static ProcurementReceiptInspectionView.LineView lineView(
            InspectionLine line, List<InspectionSplit> splits) {
        return ProcurementReceiptInspectionView.LineView.builder()
                .inspectionLineId(line.getInspectionLineId()).lineNumber(line.getLineNumber())
                .receiptLineId(line.getReceiptLineId()).purchaseOrderId(line.getPurchaseOrderId())
                .itemId(line.getItemId()).scheduleId(line.getScheduleId())
                .canonicalSkuId(line.getCanonicalSkuId()).uomCode(line.getUomCode())
                .supplierId(line.getSupplierId()).ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                .valuationPolicy(line.getValuationPolicy())
                .valuationPolicyVersion(line.getValuationPolicyVersion())
                .valuationPolicyHash(line.getValuationPolicyHash())
                .unitCostAmountMinor(line.getUnitCostAmountMinor()).currencyCode(line.getCurrencyCode())
                .receivedQuantity(line.getReceivedQuantity()).sampledQuantity(line.getSampledQuantity())
                .acceptedQuantity(line.getAcceptedQuantity()).rejectedQuantity(line.getRejectedQuantity())
                .quarantinedQuantity(line.getQuarantinedQuantity()).status(line.getStatus())
                .version(line.getVersion()).splits(splits.stream().map(ProcurementReceiptInspectionQueryService::splitView)
                        .toList()).build();
    }

    private static ProcurementReceiptInspectionPageItem pageItem(Inspection inspection) {
        return ProcurementReceiptInspectionPageItem.builder()
                .inspectionId(inspection.getInspectionId()).inspectionCode(inspection.getInspectionCode())
                .receiptId(inspection.getReceiptId()).purchaseOrderId(inspection.getPurchaseOrderId())
                .supplierId(inspection.getSupplierId()).ownerType(inspection.getOwnerType())
                .ownerId(inspection.getOwnerId()).businessNo(inspection.getBusinessNo())
                .status(inspection.getStatus()).finalDecision(inspection.getFinalDecision())
                .receivedQuantity(inspection.getReceivedQuantity()).sampledQuantity(inspection.getSampledQuantity())
                .acceptedQuantity(inspection.getAcceptedQuantity()).rejectedQuantity(inspection.getRejectedQuantity())
                .quarantinedQuantity(inspection.getQuarantinedQuantity()).version(inspection.getVersion())
                .completedAt(instant(inspection.getCompletedAt())).updatedAt(instant(inspection.getUpdatedAt()))
                .build();
    }

    private static Instant instant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ProcurementReceiptInspectionView.SplitView splitView(InspectionSplit split) {
        return ProcurementReceiptInspectionView.SplitView.builder()
                .inspectionSplitId(split.getInspectionSplitId()).splitNumber(split.getSplitNumber())
                .warehouseId(split.getWarehouseId()).locationId(split.getLocationId()).lotId(split.getLotId())
                .uomCode(split.getUomCode()).receivedQuantity(split.getReceivedQuantity())
                .sampledQuantity(split.getSampledQuantity()).acceptedQuantity(split.getAcceptedQuantity())
                .rejectedQuantity(split.getRejectedQuantity()).quarantinedQuantity(split.getQuarantinedQuantity())
                .status(split.getStatus()).version(split.getVersion()).build();
    }

    private static ProcurementReceiptInspectionView.ResultBatchView batchView(
            ResultBatch batch, List<ResultSplit> results, Map<String, List<Defect>> defectsByResult) {
        return ProcurementReceiptInspectionView.ResultBatchView.builder()
                .resultBatchId(batch.getResultBatchId())
                .inspectionVersionBefore(batch.getInspectionVersionBefore())
                .inspectionVersionAfter(batch.getInspectionVersionAfter())
                .decisionVersion(batch.getDecisionVersion())
                .actorPrincipalId(batch.getActorPrincipalId()).operationId(batch.getOperationId())
                .occurredAt(batch.getOccurredAt().toInstant(ZoneOffset.UTC))
                .splits(results.stream().map(result -> resultView(result,
                        defectsByResult.getOrDefault(result.getResultSplitId(), List.of()))).toList())
                .build();
    }

    private static ProcurementReceiptInspectionView.ResultSplitView resultView(
            ResultSplit result, List<Defect> defects) {
        return ProcurementReceiptInspectionView.ResultSplitView.builder()
                .resultSplitId(result.getResultSplitId()).qualityDecisionId(result.getQualityDecisionId())
                .decisionVersion(result.getDecisionVersion()).inspectionLineId(result.getInspectionLineId())
                .inspectionSplitId(result.getInspectionSplitId()).sampledQuantity(result.getSampledQuantity())
                .acceptedQuantity(result.getAcceptedQuantity()).rejectedQuantity(result.getRejectedQuantity())
                .quarantinedQuantity(result.getQuarantinedQuantity())
                .acceptedDispositionCode(result.getAcceptedDispositionCode())
                .rejectedDispositionCode(result.getRejectedDispositionCode())
                .quarantineDispositionCode(result.getQuarantineDispositionCode())
                .decisionEvidenceSha256(result.getDecisionEvidenceSha256()).evidenceRef(result.getEvidenceRef())
                .actorPrincipalId(result.getActorPrincipalId()).operationId(result.getOperationId())
                .financeReceiptEvidenceOperationId(result.getFinanceReceiptEvidenceOperationId())
                .financeReceiptEvidenceId(result.getFinanceReceiptEvidenceId())
                .financeReceiptEvidenceVersion(result.getFinanceReceiptEvidenceVersion())
                .financeQualityOperationId(result.getFinanceQualityOperationId())
                .financeQualityEvidenceId(result.getFinanceQualityEvidenceId())
                .financeQualityEvidenceVersion(result.getFinanceQualityEvidenceVersion())
                .acceptedInventoryOperationId(result.getAcceptedInventoryOperationId())
                .acceptedLedgerTransactionId(result.getAcceptedLedgerTransactionId())
                .acceptedInventoryAggregateVersion(result.getAcceptedInventoryAggregateVersion())
                .acceptedWarehouseOperationId(result.getAcceptedWarehouseOperationId())
                .acceptedWarehouseReceiptVersion(result.getAcceptedWarehouseReceiptVersion())
                .acceptedWarehouseReceiptLineVersion(result.getAcceptedWarehouseReceiptLineVersion())
                .acceptedWarehouseScheduleFulfillmentVersion(
                        result.getAcceptedWarehouseScheduleFulfillmentVersion())
                .acceptedFinanceInventoryOperationId(result.getAcceptedFinanceInventoryOperationId())
                .acceptedFinanceInventoryEvidenceId(result.getAcceptedFinanceInventoryEvidenceId())
                .acceptedFinanceInventoryEvidenceVersion(result.getAcceptedFinanceInventoryEvidenceVersion())
                .rejectedInventoryOperationId(result.getRejectedInventoryOperationId())
                .rejectedLedgerTransactionId(result.getRejectedLedgerTransactionId())
                .rejectedInventoryAggregateVersion(result.getRejectedInventoryAggregateVersion())
                .rejectedWarehouseOperationId(result.getRejectedWarehouseOperationId())
                .rejectedWarehouseReceiptVersion(result.getRejectedWarehouseReceiptVersion())
                .rejectedWarehouseReceiptLineVersion(result.getRejectedWarehouseReceiptLineVersion())
                .rejectedWarehouseScheduleFulfillmentVersion(
                        result.getRejectedWarehouseScheduleFulfillmentVersion())
                .rejectedFinanceInventoryOperationId(result.getRejectedFinanceInventoryOperationId())
                .rejectedFinanceInventoryEvidenceId(result.getRejectedFinanceInventoryEvidenceId())
                .rejectedFinanceInventoryEvidenceVersion(result.getRejectedFinanceInventoryEvidenceVersion())
                .quarantinedInventoryOperationId(result.getQuarantinedInventoryOperationId())
                .quarantinedLedgerTransactionId(result.getQuarantinedLedgerTransactionId())
                .quarantinedInventoryAggregateVersion(result.getQuarantinedInventoryAggregateVersion())
                .quarantinedWarehouseOperationId(result.getQuarantinedWarehouseOperationId())
                .quarantinedWarehouseReceiptVersion(result.getQuarantinedWarehouseReceiptVersion())
                .quarantinedWarehouseReceiptLineVersion(result.getQuarantinedWarehouseReceiptLineVersion())
                .quarantinedWarehouseScheduleFulfillmentVersion(
                        result.getQuarantinedWarehouseScheduleFulfillmentVersion())
                .quarantinedFinanceInventoryOperationId(result.getQuarantinedFinanceInventoryOperationId())
                .quarantinedFinanceInventoryEvidenceId(result.getQuarantinedFinanceInventoryEvidenceId())
                .quarantinedFinanceInventoryEvidenceVersion(result.getQuarantinedFinanceInventoryEvidenceVersion())
                .occurredAt(result.getOccurredAt().toInstant(ZoneOffset.UTC))
                .defects(defects.stream().map(ProcurementReceiptInspectionQueryService::defectView).toList())
                .build();
    }

    private static ProcurementReceiptInspectionView.DefectView defectView(Defect defect) {
        return ProcurementReceiptInspectionView.DefectView.builder().defectId(defect.getDefectId())
                .defectCode(defect.getDefectCode()).defectCategory(defect.getDefectCategory())
                .severity(defect.getSeverity()).affectedQuantity(defect.getAffectedQuantity())
                .evidenceSha256(defect.getEvidenceSha256()).evidenceRef(defect.getEvidenceRef()).build();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
