package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryProcurementReceiptCommand {
    private InventoryProcurementReceiptOperation operation;
    private InventoryProcurementReceiptDisposition disposition;
    private String idempotencyKey;
    private String sourceEventId;
    private String receiptId;
    private String receiptLineId;
    private String purchaseOrderId;
    private String purchaseOrderItemId;
    private String purchaseOrderScheduleId;
    private String supplierId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String baseUomCode;
    private BigDecimal quantity;
    private String qualityDecisionId;
    private Long decisionVersion;
    private String qualityEvidenceRef;
    private String valuationPolicy;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private Long unitCostAmountMinor;
    private Long movementCostAmountMinor;
    private String currencyCode;
    private String businessNo;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
