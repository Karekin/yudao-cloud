package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementReceiptInspectionView implements Serializable {
    private String inspectionId;
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
    private Instant completedAt;
    private List<LineView> lines;
    private List<ResultBatchView> resultBatches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineView implements Serializable {
        private String inspectionLineId;
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
        private List<SplitView> splits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitView implements Serializable {
        private String inspectionSplitId;
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
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultBatchView implements Serializable {
        private String resultBatchId;
        private Long inspectionVersionBefore;
        private Long inspectionVersionAfter;
        private Long decisionVersion;
        private String actorPrincipalId;
        private Long operationId;
        private Instant occurredAt;
        private List<ResultSplitView> splits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultSplitView implements Serializable {
        private String resultSplitId;
        private String qualityDecisionId;
        private Long decisionVersion;
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
        private Instant occurredAt;
        private List<DefectView> defects;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefectView implements Serializable {
        private String defectId;
        private String defectCode;
        private String defectCategory;
        private String severity;
        private BigDecimal affectedQuantity;
        private String evidenceSha256;
        private String evidenceRef;
    }
}
