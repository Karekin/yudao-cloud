package cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierReturnView implements Serializable {
    private String returnId;
    private String returnCode;
    private String purchaseOrderId;
    private String receiptId;
    private String supplierId;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String status;
    private Long version;
    private String reasonCode;
    private String remark;
    private String createdByPrincipalId;
    private String submittedByPrincipalId;
    private String approvedByPrincipalId;
    private String completedByPrincipalId;
    private String cancelledByPrincipalId;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String currentStageCode;
    private String currentStageLabel;
    private boolean terminal;
    private List<LineView> lines;
    private List<DispatchBatchView> dispatchBatches;
    private List<StatusHistoryView> statusHistory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView implements Serializable {
        private String returnLineId;
        private Integer lineNumber;
        private String receiptLineId;
        private String purchaseOrderItemId;
        private String purchaseOrderScheduleId;
        private String qualityDecisionId;
        private Long decisionVersion;
        private String inspectionSplitId;
        private SupplierReturnSourceDisposition sourceDisposition;
        private String canonicalSkuId;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private BigDecimal returnQuantity;
        private BigDecimal dispatchedQuantity;
        private BigDecimal outstandingQuantity;
        private String uomCode;
        private String lineStatus;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String qualityEvidenceRef;
        private String remark;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchBatchView implements Serializable {
        private String batchId;
        private String batchNo;
        private String status;
        private Long version;
        private String dispatchedByPrincipalId;
        private String remark;
        private LocalDateTime occurredAt;
        private List<DispatchLineView> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchLineView implements Serializable {
        private String executionLineId;
        private String returnLineId;
        private Integer lineNumber;
        private SupplierReturnSourceDisposition sourceDisposition;
        private BigDecimal dispatchedQuantity;
        private BigDecimal cumulativeDispatchedQuantity;
        private BigDecimal outstandingQuantity;
        private Long inventoryOperationId;
        private Long ledgerTransactionId;
        private String sourceBalanceId;
        private Long inventoryAggregateVersion;
        private String lineStatus;
        private String remark;
        private LocalDateTime occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryView implements Serializable {
        private String historyId;
        private String businessObjectType;
        private String businessObjectId;
        private String status;
        private Long statusVersion;
        private String stageCode;
        private String stageLabel;
        private String remark;
        private LocalDateTime changedAt;
    }
}
