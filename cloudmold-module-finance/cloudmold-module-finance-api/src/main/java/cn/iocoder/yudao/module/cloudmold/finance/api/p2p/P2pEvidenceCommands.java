package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public final class P2pEvidenceCommands {
    private P2pEvidenceCommands() {
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PurchaseOrderLine implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String sourceEventId;
        private Long sourceVersion;
        private String evidenceSha256;
        private Instant sourceOccurredAt;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String deliveryScheduleId;
        private String legalEntityId;
        private String supplierId;
        private String currencyCode;
        private BigDecimal orderedQuantity;
        private String unitOfMeasure;
        private BigDecimal unitNetPrice;
        private Long netAmountMinor;
        private Long taxAmountMinor;
        private Long grossAmountMinor;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReceiptLine implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String sourceEventId;
        private Long sourceVersion;
        private String evidenceSha256;
        private Instant sourceOccurredAt;
        private String receiptId;
        private String receiptLineId;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private Long purchaseOrderLineVersion;
        private String deliveryScheduleId;
        private BigDecimal receivedQuantity;
        private String unitOfMeasure;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QualityDisposition implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String sourceEventId;
        private Long sourceVersion;
        private String evidenceSha256;
        private Instant sourceOccurredAt;
        private String qualityDispositionId;
        private String receiptLineId;
        private Long receiptLineVersion;
        private String purchaseOrderItemId;
        private BigDecimal inspectedQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal heldQuantity;
        private String unitOfMeasure;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InventoryMovement implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String sourceEventId;
        private Long sourceVersion;
        private String evidenceSha256;
        private Instant sourceOccurredAt;
        private String inventoryMovementId;
        private String receiptLineId;
        private String qualityDispositionId;
        private Long qualityDispositionVersion;
        private String disposition;
        private String purchaseOrderItemId;
        private BigDecimal movementQuantity;
        private String unitOfMeasure;
        private Long movementCostAmountMinor;
        private Long unitCostAmountMinor;
        private String currencyCode;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PostQualifiedReceipt implements Serializable {
        private FinanceCommandEnvelope envelope;
        private String inventoryMovementId;
        private Long inventoryMovementVersion;
        private String ledgerId;
        private String accountingPeriodId;
        private java.time.LocalDate accountingDate;
        private String postingRuleId;
        private Long postingRuleVersion;
        private java.util.List<JournalDimensionAssignment> journalDimensions;
    }
}
