package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_procurement_receipt_line")
public class ReceiptLineDO {
    private String receiptLineId;
    private Long tenantId;
    private String receiptId;
    private Integer lineNo;
    private String asnLineId;
    private String procurementOrderId;
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
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQualityQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private BigDecimal cumulativePutawayQuantity;
    private String lotId;
    private String qualityStatus;
    private String qualityInspectionId;
    private String valuationPolicyId;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private Long unitCostAmountMinor;
    private Long movementCostAmountMinor;
    private String currencyCode;
    private String roundingPolicyCode;
    private String tolerancePolicyVersion;
    private String tolerancePolicyHash;
    private String inventoryIdempotencyKey;
    private Long inventoryOperationId;
    private Long inventoryLedgerTxId;
    private String inventoryBalanceId;
    private Long financeReceiptEvidenceOperationId;
    private String financeReceiptEvidenceId;
    private Long financeReceiptEvidenceVersion;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
