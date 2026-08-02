package cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.InventoryScrapPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDispositionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDispositionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDocumentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InventoryScrapQueryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InventoryScrapStoreMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryScrapQueryService implements InventoryScrapQueryApi {

    private final InventoryScrapStoreMapper mapper;
    private final InventoryScrapQueryMapper queryMapper;

    @Override
    public InventoryScrapView requireByScrapId(String scrapId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        InventoryScrapDocumentDO document = mapper.selectDocument(tenantId, requireText(scrapId, "scrapId"));
        if (document == null) {
            throw new IllegalArgumentException("inventory scrap document not found");
        }
        List<InventoryScrapLineDO> lines = mapper.selectLines(tenantId, document.getScrapId());
        List<InventoryScrapDispositionBatchDO> batches = mapper.selectDispositionBatches(tenantId, document.getScrapId());
        List<InventoryScrapDispositionLineDO> dispositionLines = mapper.selectDispositionLines(tenantId, document.getScrapId());
        List<InventoryScrapHistoryDO> history = mapper.selectHistory(tenantId, document.getScrapId());
        Map<String, List<InventoryScrapDispositionLineDO>> batchLines = new LinkedHashMap<>();
        for (InventoryScrapDispositionLineDO line : dispositionLines) {
            batchLines.computeIfAbsent(line.getBatchId(), ignored -> new ArrayList<>()).add(line);
        }
        return InventoryScrapView.builder()
                .scrapId(document.getScrapId()).scrapCode(document.getScrapCode())
                .reasonCode(document.getReasonCode()).remark(document.getRemark())
                .ownerType(document.getOwnerType()).ownerId(document.getOwnerId())
                .warehouseId(document.getWarehouseId()).scrapStatus(document.getStatus())
                .aggregateVersion(document.getVersion()).totalRequestedQuantity(document.getTotalRequestedQuantity())
                .totalDisposedQuantity(document.getTotalDisposedQuantity()).lineCount(document.getLineCount())
                .requestedByPrincipalId(document.getRequestedByPrincipalId())
                .submittedByPrincipalId(document.getSubmittedByPrincipalId())
                .approvedByPrincipalId(document.getApprovedByPrincipalId())
                .completedByPrincipalId(document.getCompletedByPrincipalId())
                .cancelledByPrincipalId(document.getCancelledByPrincipalId())
                .approvedAt(document.getApprovedAt()).completedAt(document.getCompletedAt())
                .cancelledAt(document.getCancelledAt()).createdAt(document.getCreatedAt()).updatedAt(document.getUpdatedAt())
                .lines(lines.stream().map(line -> InventoryScrapView.LineView.builder()
                        .lineId(line.getLineId()).lineNumber(line.getLineNumber()).canonicalSkuId(line.getCanonicalSkuId())
                        .locationId(line.getLocationId()).lotId(line.getLotId()).stockStatus(line.getStockStatus())
                        .qualityStatus(line.getQualityStatus()).baseUomCode(line.getBaseUomCode())
                        .requestedQuantity(line.getRequestedQuantity()).disposedQuantity(line.getDisposedQuantity())
                        .evidenceType(line.getEvidenceType()).evidenceRef(line.getEvidenceRef())
                        .lineStatus(line.getStatus()).aggregateVersion(line.getVersion()).remark(line.getRemark()).build()).toList())
                .dispositionBatches(batches.stream().map(batch -> InventoryScrapView.DispositionBatchView.builder()
                        .batchId(batch.getBatchId()).batchNo(batch.getBatchNo()).dispositionType(batch.getDispositionType())
                        .proofType(batch.getProofType()).proofRef(batch.getProofRef()).batchStatus(batch.getStatus())
                        .totalDisposedQuantity(batch.getTotalDisposedQuantity()).lineCount(batch.getLineCount())
                        .executedByPrincipalId(batch.getExecutedByPrincipalId()).occurredAt(batch.getOccurredAt())
                        .remark(batch.getRemark())
                        .lines(batchLines.getOrDefault(batch.getBatchId(), List.of()).stream().map(line ->
                                InventoryScrapView.DispositionLineView.builder()
                                        .dispositionLineId(line.getDispositionLineId()).scrapLineId(line.getScrapLineId())
                                        .lineNumber(line.getLineNumber()).canonicalSkuId(line.getCanonicalSkuId())
                                        .locationId(line.getLocationId()).lotId(line.getLotId())
                                        .stockStatus(line.getStockStatus()).qualityStatus(line.getQualityStatus())
                                        .baseUomCode(line.getBaseUomCode()).disposedQuantity(line.getDisposedQuantity())
                                        .cumulativeDisposedQuantity(line.getCumulativeDisposedQuantity())
                                        .inventoryOperationId(line.getInventoryOperationId())
                                        .inventoryLedgerTransactionId(line.getInventoryLedgerTransactionId())
                                        .inventoryBalanceId(line.getInventoryBalanceId()).lineStatus(line.getStatus())
                                        .remark(line.getRemark()).build()).toList())
                        .build()).toList())
                .statusHistory(history.stream().map(item -> InventoryScrapView.StatusHistoryView.builder()
                        .historyId(item.getHistoryId()).scrapStatus(item.getStatus()).aggregateVersion(item.getStatusVersion())
                        .actorPrincipalId(item.getActorPrincipalId()).note(item.getNote()).changedAt(item.getChangedAt()).build()).toList())
                .build();
    }

    public PageResult<InventoryScrapPageItem> getPage(InventoryScrapPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = normalize(request.getKeyword());
        String status = normalize(request.getStatus());
        String warehouseId = normalize(request.getWarehouseId());
        long total = queryMapper.countPage(tenantId, keyword, status, warehouseId);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectPage(tenantId, keyword, status, warehouseId, offset, request.getPageSize()), total);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    @Data
    public static class InventoryScrapPageItem {
        @Schema(description = "报废单 ID")
        private String scrapId;
        @Schema(description = "报废单号")
        private String scrapCode;
        @Schema(description = "报废原因码")
        private String reasonCode;
        @Schema(description = "货主类型")
        private String ownerType;
        @Schema(description = "货主 ID")
        private String ownerId;
        @Schema(description = "规范仓库 ID")
        private String warehouseId;
        @Schema(description = "单据状态")
        private String scrapStatus;
        @Schema(description = "申请总数量")
        private BigDecimal totalRequestedQuantity;
        @Schema(description = "已处置总数量")
        private BigDecimal totalDisposedQuantity;
        @Schema(description = "行数")
        private Integer lineCount;
        @Schema(description = "聚合版本")
        private Long aggregateVersion;
        @Schema(description = "创建时间")
        private LocalDateTime createdAt;
        @Schema(description = "更新时间")
        private LocalDateTime updatedAt;
    }
}
