package cn.iocoder.yudao.module.cloudmold.warehouse.api;

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
public class StockCountView {
    private String stockCountId;
    private String stockCountCode;
    private String countMode;
    private String scopeType;
    private String scopeLabel;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String reasonCode;
    private String remark;
    private String status;
    private Long version;
    private Long freezeLedgerTransactionId;
    private LocalDateTime freezeCapturedAt;
    private Integer lineCount;
    private Integer countedLineCount;
    private Integer differenceLineCount;
    private String createdByPrincipalId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LineView> lines;
    private List<ExecutionBatchView> executionBatches;
    private List<ApprovalView> approvals;
    private List<StatusHistoryView> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView {
        private String lineId;
        private Integer lineNumber;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private String stockStatus;
        private String qualityStatus;
        private String baseUomCode;
        private String balanceId;
        private BigDecimal bookOnHandQuantity;
        private BigDecimal bookReservedQuantity;
        private BigDecimal bookInTransitQuantity;
        private BigDecimal bookAvailableQuantity;
        private Long bookAggregateVersion;
        private BigDecimal countedOnHandQuantity;
        private BigDecimal differenceQuantity;
        private String countStatus;
        private String countedByPrincipalId;
        private LocalDateTime countedAt;
        private String adjustmentId;
        private Long adjustmentLedgerTransactionId;
        private Long adjustedAggregateVersion;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionBatchView {
        private String batchId;
        private String batchNo;
        private String status;
        private Integer lineCount;
        private String countedByPrincipalId;
        private LocalDateTime occurredAt;
        private String remark;
        private List<ExecutionLineView> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionLineView {
        private String executionLineId;
        private String stockCountLineId;
        private BigDecimal countedOnHandQuantity;
        private BigDecimal differenceQuantity;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalView {
        private String approvalId;
        private String approvalType;
        private String approvedByPrincipalId;
        private LocalDateTime approvedAt;
        private BigDecimal totalBookOnHandQuantity;
        private BigDecimal totalCountedOnHandQuantity;
        private BigDecimal totalDifferenceQuantity;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryView {
        private String historyId;
        private String status;
        private Long statusVersion;
        private String stageCode;
        private String stageLabel;
        private String changedByPrincipalId;
        private String remark;
        private LocalDateTime changedAt;
    }
}
