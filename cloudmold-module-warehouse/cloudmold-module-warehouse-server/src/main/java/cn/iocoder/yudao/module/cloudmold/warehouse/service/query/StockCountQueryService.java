package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockCountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountDifferenceApprovalDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountExecutionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountExecutionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockCountStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockCountQueryService implements StockCountQueryApi {

    private final StockCountStoreMapper mapper;

    public PageResult<StockCountPageItem> getPage(StockCountPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = blankToNull(request.getKeyword());
        String status = blankToNull(request.getStatus());
        String countMode = blankToNull(request.getCountMode());
        long total = mapper.countPage(tenantId, keyword, status, countMode);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L);
        }
        int offset = (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(mapper.selectPage(tenantId, keyword, status, countMode, request.getPageSize(), offset),
                total);
    }

    @Override
    public StockCountView requireByStockCountId(String stockCountId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        StockCountDO stockCount = mapper.selectStockCount(tenantId, requireText(stockCountId, "stockCountId"));
        if (stockCount == null) {
            throw new IllegalArgumentException("stock count not found");
        }
        List<StockCountLineDO> lines = mapper.selectLines(tenantId, stockCountId);
        List<StockCountExecutionBatchDO> batches = mapper.selectExecutionBatches(tenantId, stockCountId);
        List<StockCountExecutionLineDO> executionLines = mapper.selectExecutionLines(tenantId, stockCountId);
        List<StockCountDifferenceApprovalDO> approvals = mapper.selectApprovals(tenantId, stockCountId);
        List<StockCountStatusHistoryDO> history = mapper.selectStatusHistory(tenantId, stockCountId);

        Map<String, List<StockCountView.ExecutionLineView>> linesByBatch = new LinkedHashMap<>();
        for (StockCountExecutionLineDO executionLine : executionLines) {
            linesByBatch.computeIfAbsent(executionLine.getBatchId(), ignored -> new ArrayList<>()).add(
                    StockCountView.ExecutionLineView.builder()
                            .executionLineId(executionLine.getExecutionLineId())
                            .stockCountLineId(executionLine.getStockCountLineId())
                            .countedOnHandQuantity(executionLine.getCountedOnHandQuantity())
                            .differenceQuantity(executionLine.getDifferenceQuantity())
                            .remark(executionLine.getRemark())
                            .build());
        }
        return StockCountView.builder()
                .stockCountId(stockCount.getStockCountId())
                .stockCountCode(stockCount.getStockCountCode())
                .countMode(stockCount.getCountMode())
                .scopeType(stockCount.getScopeType())
                .scopeLabel(stockCount.getScopeLabel())
                .sourceBusinessType(stockCount.getSourceBusinessType())
                .sourceBusinessRef(stockCount.getSourceBusinessRef())
                .reasonCode(stockCount.getReasonCode())
                .remark(stockCount.getRemark())
                .status(stockCount.getStatus())
                .version(stockCount.getVersion())
                .freezeLedgerTransactionId(stockCount.getFreezeLedgerTransactionId())
                .freezeCapturedAt(stockCount.getFreezeCapturedAt())
                .lineCount(stockCount.getLineCount())
                .countedLineCount(stockCount.getCountedLineCount())
                .differenceLineCount(stockCount.getDifferenceLineCount())
                .createdByPrincipalId(stockCount.getCreatedByPrincipalId())
                .createdAt(stockCount.getCreatedAt())
                .updatedAt(stockCount.getUpdatedAt())
                .lines(lines.stream().map(line -> StockCountView.LineView.builder()
                        .lineId(line.getLineId())
                        .lineNumber(line.getLineNumber())
                        .ownerType(line.getOwnerType())
                        .ownerId(line.getOwnerId())
                        .canonicalSkuId(line.getCanonicalSkuId())
                        .warehouseId(line.getWarehouseId())
                        .locationId(line.getLocationId())
                        .lotId(line.getLotId())
                        .stockStatus(line.getStockStatus())
                        .qualityStatus(line.getQualityStatus())
                        .baseUomCode(line.getBaseUomCode())
                        .balanceId(line.getBalanceId())
                        .bookOnHandQuantity(line.getBookOnHandQuantity())
                        .bookReservedQuantity(line.getBookReservedQuantity())
                        .bookInTransitQuantity(line.getBookInTransitQuantity())
                        .bookAvailableQuantity(line.getBookAvailableQuantity())
                        .bookAggregateVersion(line.getBookAggregateVersion())
                        .countedOnHandQuantity(line.getCountedOnHandQuantity())
                        .differenceQuantity(line.getDifferenceQuantity())
                        .countStatus(line.getCountStatus())
                        .countedByPrincipalId(line.getCountedByPrincipalId())
                        .countedAt(line.getCountedAt())
                        .adjustmentId(line.getAdjustmentId())
                        .adjustmentLedgerTransactionId(line.getAdjustmentLedgerTransactionId())
                        .adjustedAggregateVersion(line.getAdjustedAggregateVersion())
                        .remark(line.getRemark())
                        .build()).toList())
                .executionBatches(batches.stream().map(batch -> StockCountView.ExecutionBatchView.builder()
                        .batchId(batch.getBatchId())
                        .batchNo(batch.getBatchNo())
                        .status(batch.getStatus())
                        .lineCount(batch.getLineCount())
                        .countedByPrincipalId(batch.getCountedByPrincipalId())
                        .occurredAt(batch.getOccurredAt())
                        .remark(batch.getRemark())
                        .lines(linesByBatch.getOrDefault(batch.getBatchId(), List.of()))
                        .build()).toList())
                .approvals(approvals.stream().map(approval -> StockCountView.ApprovalView.builder()
                        .approvalId(approval.getApprovalId())
                        .approvalType(approval.getApprovalType())
                        .approvedByPrincipalId(approval.getApprovedByPrincipalId())
                        .approvedAt(approval.getApprovedAt())
                        .totalBookOnHandQuantity(approval.getTotalBookOnHandQuantity())
                        .totalCountedOnHandQuantity(approval.getTotalCountedOnHandQuantity())
                        .totalDifferenceQuantity(approval.getTotalDifferenceQuantity())
                        .remark(approval.getRemark())
                        .build()).toList())
                .statusHistory(history.stream().map(item -> StockCountView.StatusHistoryView.builder()
                        .historyId(item.getHistoryId())
                        .status(item.getStatus())
                        .statusVersion(item.getStatusVersion())
                        .stageCode(item.getStageCode())
                        .stageLabel(item.getStageLabel())
                        .changedByPrincipalId(item.getChangedByPrincipalId())
                        .remark(item.getRemark())
                        .changedAt(item.getChangedAt())
                        .build()).toList())
                .build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
