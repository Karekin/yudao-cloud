package cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ProcurementReceiptInspectionRecords {
    private ProcurementReceiptInspectionRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String operationType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String inspectionId;
        private Long aggregateVersion;
        private String resultStatus;
        private String finalDecision;
        private BigDecimal receivedQuantity;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
    }

    @Data
    @Accessors(chain = true)
    public static class Inspection {
        private String inspectionId;
        private Long tenantId;
        private String inspectionCode;
        private String receiptId;
        private String purchaseOrderId;
        private String supplierId;
        private String ownerType;
        private String ownerId;
        private String businessNo;
        private String standardId;
        private Long standardVersion;
        private String standardVersionId;
        private String standardContentSha256;
        private String createdByPrincipalId;
        private String lastDecisionActorPrincipalId;
        private String completedByPrincipalId;
        private String status;
        private String finalDecision;
        private BigDecimal receivedQuantity;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private Long version;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InspectionLine {
        private String inspectionLineId;
        private Long tenantId;
        private String inspectionId;
        private Integer lineNumber;
        private String receiptLineId;
        private String purchaseOrderId;
        private String itemId;
        private String scheduleId;
        private String canonicalSkuId;
        private String uomCode;
        private String supplierId;
        private String ownerType;
        private String ownerId;
        private String valuationPolicy;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private BigDecimal receivedQuantity;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private String status;
        private Long version;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InspectionSplit {
        private String inspectionSplitId;
        private Long tenantId;
        private String inspectionId;
        private String inspectionLineId;
        private Integer splitNumber;
        private String warehouseId;
        private String locationId;
        private String lotId;
        private String uomCode;
        private BigDecimal receivedQuantity;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private String status;
        private Long version;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ResultBatch {
        private String resultBatchId;
        private Long tenantId;
        private String inspectionId;
        private Long inspectionVersionBefore;
        private Long inspectionVersionAfter;
        private Long decisionVersion;
        private String actorPrincipalId;
        private Long operationId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ResultSplit {
        private String resultSplitId;
        private String qualityDecisionId;
        private Long decisionVersion;
        private Long tenantId;
        private String resultBatchId;
        private String inspectionId;
        private String inspectionLineId;
        private String inspectionSplitId;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private String acceptedDispositionCode;
        private String rejectedDispositionCode;
        private String quarantineDispositionCode;
        private String decisionEvidenceSha256;
        private String evidenceRef;
        private String actorPrincipalId;
        private Long operationId;
        private Long financeReceiptEvidenceOperationId;
        private String financeReceiptEvidenceId;
        private Long financeReceiptEvidenceVersion;
        private Long financeQualityOperationId;
        private String financeQualityEvidenceId;
        private Long financeQualityEvidenceVersion;
        private Long acceptedInventoryOperationId;
        private Long acceptedLedgerTransactionId;
        private Long acceptedInventoryAggregateVersion;
        private Long acceptedWarehouseOperationId;
        private Long acceptedWarehouseReceiptVersion;
        private Long acceptedWarehouseReceiptLineVersion;
        private Long acceptedWarehouseScheduleFulfillmentVersion;
        private Long acceptedFinanceInventoryOperationId;
        private String acceptedFinanceInventoryEvidenceId;
        private Long acceptedFinanceInventoryEvidenceVersion;
        private Long rejectedInventoryOperationId;
        private Long rejectedLedgerTransactionId;
        private Long rejectedInventoryAggregateVersion;
        private Long rejectedWarehouseOperationId;
        private Long rejectedWarehouseReceiptVersion;
        private Long rejectedWarehouseReceiptLineVersion;
        private Long rejectedWarehouseScheduleFulfillmentVersion;
        private Long rejectedFinanceInventoryOperationId;
        private String rejectedFinanceInventoryEvidenceId;
        private Long rejectedFinanceInventoryEvidenceVersion;
        private Long quarantinedInventoryOperationId;
        private Long quarantinedLedgerTransactionId;
        private Long quarantinedInventoryAggregateVersion;
        private Long quarantinedWarehouseOperationId;
        private Long quarantinedWarehouseReceiptVersion;
        private Long quarantinedWarehouseReceiptLineVersion;
        private Long quarantinedWarehouseScheduleFulfillmentVersion;
        private Long quarantinedFinanceInventoryOperationId;
        private String quarantinedFinanceInventoryEvidenceId;
        private Long quarantinedFinanceInventoryEvidenceVersion;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Defect {
        private String defectId;
        private Long tenantId;
        private String resultSplitId;
        private String inspectionId;
        private String inspectionLineId;
        private String inspectionSplitId;
        private String defectCode;
        private String defectCategory;
        private String severity;
        private BigDecimal affectedQuantity;
        private String evidenceSha256;
        private String evidenceRef;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InspectionHistory {
        private Long tenantId;
        private String inspectionId;
        private Long inspectionVersion;
        private String previousStatus;
        private String currentStatus;
        private String finalDecision;
        private String actorPrincipalId;
        private Long operationId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class LineHistory {
        private Long tenantId;
        private String inspectionLineId;
        private Long lineVersion;
        private String previousStatus;
        private String currentStatus;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private String actorPrincipalId;
        private Long operationId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class QuantityTotals {
        private BigDecimal receivedQuantity;
        private BigDecimal sampledQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private Long totalLines;
        private Long completedLines;
    }
}
