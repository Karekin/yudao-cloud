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
public class InboundReceiptProgressView {
    private String procurementOrderId;
    private String asnId;
    private String asnNo;
    private String asnStatus;
    private Long asnVersion;
    private String supplierId;
    private String warehouseId;
    private BigDecimal totalScheduledQuantity;
    private BigDecimal totalReceivedQuantity;
    private BigDecimal totalPendingQualityQuantity;
    private Integer receiptCount;
    private String nextWaitingEventCode;
    private String nextWaitingEventLabel;
    private boolean terminal;
    private List<AsnLineView> lines;
    private List<ReceiptView> receipts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsnLineView {
        private String asnLineId;
        private Integer lineNo;
        private String procurementOrderItemId;
        private String deliveryScheduleId;
        private Long poReleaseVersion;
        private Long fulfillmentVersion;
        private String supplierId;
        private String warehouseId;
        private String receiptLocationId;
        private String canonicalSkuId;
        private String ownerType;
        private String ownerId;
        private String baseUomCode;
        private BigDecimal scheduledQuantity;
        private BigDecimal allowedOverReceiptQuantity;
        private BigDecimal receivedQuantity;
        private BigDecimal pendingQualityQuantity;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private String unitCostAmountMinor;
        private String currencyCode;
        private String roundingPolicyCode;
        private String tolerancePolicyVersion;
        private String tolerancePolicyHash;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptView {
        private String receiptId;
        private String receiptNo;
        private String status;
        private Long version;
        private String remark;
        private LocalDateTime createdAt;
        private List<ReceiptLineView> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiptLineView {
        private String receiptLineId;
        private Integer lineNo;
        private String asnLineId;
        private String procurementOrderItemId;
        private String deliveryScheduleId;
        private Long poReleaseVersion;
        private Long fulfillmentVersionBefore;
        private Long fulfillmentVersionAfter;
        private String supplierId;
        private String warehouseId;
        private String receiptLocationId;
        private String canonicalSkuId;
        private String ownerType;
        private String ownerId;
        private String baseUomCode;
        private String lotId;
        private String qualityStatus;
        private String qualityInspectionId;
        private BigDecimal receivedQuantity;
        private BigDecimal pendingQualityQuantity;
        private BigDecimal acceptedQuantity;
        private BigDecimal rejectedQuantity;
        private BigDecimal quarantinedQuantity;
        private BigDecimal cumulativePutawayQuantity;
        private Long version;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private String unitCostAmountMinor;
        private String movementCostAmountMinor;
        private String currencyCode;
        private String roundingPolicyCode;
        private String tolerancePolicyVersion;
        private String tolerancePolicyHash;
        private String inventoryOperationId;
        private String inventoryLedgerTxId;
        private String inventoryBalanceId;
        private String financeReceiptEvidenceOperationId;
        private String financeReceiptEvidenceId;
        private Long financeReceiptEvidenceVersion;
    }
}
