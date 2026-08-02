package cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnSourceDisposition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.SupplierReturnPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnQueryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierReturnQueryService implements SupplierReturnQueryApi {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final SupplierReturnStoreMapper storeMapper;
    private final SupplierReturnQueryMapper queryMapper;

    @Override
    public SupplierReturnView requireByReturnId(String returnId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        SupplierReturnDocumentDO document = storeMapper.selectDocument(tenantId, requireText(returnId, "returnId"));
        if (document == null) {
            throw new IllegalArgumentException("supplier return not found");
        }
        List<SupplierReturnLineDO> lines = storeMapper.selectLines(tenantId, returnId);
        List<SupplierReturnDispatchBatchDO> batches = storeMapper.selectDispatchBatches(tenantId, returnId);
        List<SupplierReturnDispatchLineDO> dispatchLines = storeMapper.selectDispatchLines(tenantId, returnId);
        List<SupplierReturnStatusHistoryDO> history = storeMapper.selectStatusHistory(tenantId, returnId);
        Map<String, List<SupplierReturnDispatchLineDO>> batchLineMap = new LinkedHashMap<>();
        for (SupplierReturnDispatchLineDO line : dispatchLines) {
            batchLineMap.computeIfAbsent(line.getBatchId(), ignored -> new ArrayList<>()).add(line);
        }
        Stage stage = stage(document.getStatus());
        return SupplierReturnView.builder()
                .returnId(document.getReturnId()).returnCode(document.getReturnCode())
                .purchaseOrderId(document.getPurchaseOrderId()).receiptId(document.getReceiptId())
                .supplierId(document.getSupplierId()).ownerType(document.getOwnerType()).ownerId(document.getOwnerId())
                .warehouseId(document.getWarehouseId()).status(document.getStatus()).version(document.getVersion())
                .reasonCode(document.getReasonCode()).remark(document.getRemark())
                .createdByPrincipalId(document.getCreatedByPrincipalId())
                .submittedByPrincipalId(document.getSubmittedByPrincipalId())
                .approvedByPrincipalId(document.getApprovedByPrincipalId())
                .completedByPrincipalId(document.getCompletedByPrincipalId())
                .cancelledByPrincipalId(document.getCancelledByPrincipalId())
                .submittedAt(document.getSubmittedAt()).approvedAt(document.getApprovedAt())
                .completedAt(document.getCompletedAt()).cancelledAt(document.getCancelledAt())
                .createdAt(document.getCreatedAt()).updatedAt(document.getUpdatedAt())
                .currentStageCode(stage.code()).currentStageLabel(stage.label()).terminal(stage.terminal())
                .lines(lines.stream().map(line -> SupplierReturnView.LineView.builder()
                        .returnLineId(line.getReturnLineId()).lineNumber(line.getLineNumber())
                        .receiptLineId(line.getReceiptLineId()).purchaseOrderItemId(line.getPurchaseOrderItemId())
                        .purchaseOrderScheduleId(line.getPurchaseOrderScheduleId())
                        .qualityDecisionId(line.getQualityDecisionId()).decisionVersion(line.getDecisionVersion())
                        .inspectionSplitId(line.getInspectionSplitId())
                        .sourceDisposition(SupplierReturnSourceDisposition.valueOf(line.getSourceDisposition()))
                        .canonicalSkuId(line.getCanonicalSkuId()).warehouseId(line.getWarehouseId())
                        .locationId(line.getLocationId()).lotId(line.getLotId())
                        .returnQuantity(scale(line.getReturnQuantity())).dispatchedQuantity(scale(line.getDispatchedQuantity()))
                        .outstandingQuantity(scale(line.getOutstandingQuantity())).uomCode(line.getUomCode())
                        .lineStatus(line.getStatus()).valuationPolicyId(line.getValuationPolicyId())
                        .valuationPolicyVersion(line.getValuationPolicyVersion())
                        .valuationPolicyHash(line.getValuationPolicyHash())
                        .unitCostAmountMinor(line.getUnitCostAmountMinor()).currencyCode(line.getCurrencyCode())
                        .qualityEvidenceRef(line.getQualityEvidenceRef()).remark(line.getRemark()).build()).toList())
                .dispatchBatches(batches.stream().map(batch -> SupplierReturnView.DispatchBatchView.builder()
                        .batchId(batch.getBatchId()).batchNo(batch.getBatchNo()).status(batch.getStatus())
                        .version(batch.getVersion()).dispatchedByPrincipalId(batch.getDispatchedByPrincipalId())
                        .remark(batch.getRemark()).occurredAt(batch.getOccurredAt())
                        .lines(batchLineMap.getOrDefault(batch.getBatchId(), List.of()).stream().map(line ->
                                SupplierReturnView.DispatchLineView.builder()
                                        .executionLineId(line.getExecutionLineId()).returnLineId(line.getReturnLineId())
                                        .lineNumber(line.getLineNumber())
                                        .sourceDisposition(SupplierReturnSourceDisposition.valueOf(line.getSourceDisposition()))
                                        .dispatchedQuantity(scale(line.getDispatchedQuantity()))
                                        .cumulativeDispatchedQuantity(scale(line.getCumulativeDispatchedQuantity()))
                                        .outstandingQuantity(scale(line.getOutstandingQuantity()))
                                        .inventoryOperationId(line.getInventoryOperationId())
                                        .ledgerTransactionId(line.getLedgerTransactionId())
                                        .sourceBalanceId(line.getSourceBalanceId())
                                        .inventoryAggregateVersion(line.getInventoryAggregateVersion())
                                        .lineStatus(line.getStatus()).remark(line.getRemark())
                                        .occurredAt(line.getOccurredAt()).build()).toList())
                        .build()).toList())
                .statusHistory(history.stream().map(item -> SupplierReturnView.StatusHistoryView.builder()
                        .historyId(item.getHistoryId()).businessObjectType(item.getBusinessObjectType())
                        .businessObjectId(item.getBusinessObjectId()).status(item.getStatus())
                        .statusVersion(item.getStatusVersion()).stageCode(item.getStageCode())
                        .stageLabel(item.getStageLabel()).remark(item.getRemark()).changedAt(item.getChangedAt())
                        .build()).toList())
                .build();
    }

    public PageResult<SupplierReturnPageItem> getPage(SupplierReturnPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = trimToNull(request.getKeyword());
        String status = trimToNull(request.getStatus());
        String purchaseOrderId = trimToNull(request.getPurchaseOrderId());
        String receiptId = trimToNull(request.getReceiptId());
        String supplierId = trimToNull(request.getSupplierId());
        String warehouseId = trimToNull(request.getWarehouseId());
        long total = queryMapper.countPage(tenantId, keyword, status, purchaseOrderId, receiptId, supplierId, warehouseId);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<SupplierReturnPageItem> rows = queryMapper.selectPage(tenantId, keyword, status, purchaseOrderId,
                receiptId, supplierId, warehouseId, offset, request.getPageSize());
        rows.forEach(row -> {
            Stage stage = stage(row.getStatus());
            row.setCurrentStageCode(stage.code());
            row.setCurrentStageLabel(stage.label());
            row.setTerminal(stage.terminal());
            row.setTotalReturnQuantity(scale(row.getTotalReturnQuantity()));
            row.setTotalDispatchedQuantity(scale(row.getTotalDispatchedQuantity()));
        });
        return new PageResult<>(rows, total);
    }

    private static Stage stage(String status) {
        return switch (status) {
            case "DRAFT" -> new Stage("DRAFT", "草稿待提交", false);
            case "SUBMITTED" -> new Stage("APPROVAL", "等待批准", false);
            case "APPROVED", "PARTIALLY_DISPATCHED" -> new Stage("RETURN_TO_SUPPLIER", "等待退供发运", false);
            case "DISPATCHED" -> new Stage("COMPLETE", "等待退供完成", false);
            case "COMPLETED" -> new Stage("NONE", "退供已完成", true);
            case "CANCELLED" -> new Stage("NONE", "退供已取消", true);
            default -> throw new IllegalArgumentException("unsupported supplier return status: " + status);
        };
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private record Stage(String code, String label, boolean terminal) {
    }
}
