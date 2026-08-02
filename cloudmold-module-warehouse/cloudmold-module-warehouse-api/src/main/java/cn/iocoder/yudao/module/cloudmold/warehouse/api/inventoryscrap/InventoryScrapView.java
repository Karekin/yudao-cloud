package cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryScrapView {
    private String scrapId;
    private String scrapCode;
    private String reasonCode;
    private String remark;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String scrapStatus;
    private Long aggregateVersion;
    private BigDecimal totalRequestedQuantity;
    private BigDecimal totalDisposedQuantity;
    private Integer lineCount;
    private String requestedByPrincipalId;
    private String submittedByPrincipalId;
    private String approvedByPrincipalId;
    private String completedByPrincipalId;
    private String cancelledByPrincipalId;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LineView> lines;
    private List<DispositionBatchView> dispositionBatches;
    private List<StatusHistoryView> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView {
        private String lineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private String locationId;
        private String lotId;
        private String stockStatus;
        private String qualityStatus;
        private String baseUomCode;
        private BigDecimal requestedQuantity;
        private BigDecimal disposedQuantity;
        private String evidenceType;
        private String evidenceRef;
        private String lineStatus;
        private Long aggregateVersion;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispositionBatchView {
        private String batchId;
        private String batchNo;
        private String dispositionType;
        private String proofType;
        private String proofRef;
        private String batchStatus;
        private BigDecimal totalDisposedQuantity;
        private Integer lineCount;
        private String executedByPrincipalId;
        private LocalDateTime occurredAt;
        private String remark;
        private List<DispositionLineView> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispositionLineView {
        private String dispositionLineId;
        private String scrapLineId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private String locationId;
        private String lotId;
        private String stockStatus;
        private String qualityStatus;
        private String baseUomCode;
        private BigDecimal disposedQuantity;
        private BigDecimal cumulativeDisposedQuantity;
        private Long inventoryOperationId;
        private Long inventoryLedgerTransactionId;
        private String inventoryBalanceId;
        private String lineStatus;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryView {
        private Long historyId;
        private String scrapStatus;
        private Long aggregateVersion;
        private String actorPrincipalId;
        private String note;
        private LocalDateTime changedAt;
    }
}
