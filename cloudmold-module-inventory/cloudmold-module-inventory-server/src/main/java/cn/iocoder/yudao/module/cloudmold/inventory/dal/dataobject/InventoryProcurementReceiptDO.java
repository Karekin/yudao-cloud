package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_procurement_receipt_v3")
public class InventoryProcurementReceiptDO {
    @TableId(type = IdType.INPUT)
    private String receiptLineId;
    private Long tenantId;
    private String receiptId;
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
    private String valuationPolicy;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private Long unitCostAmountMinor;
    private String currencyCode;
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private BigDecimal returnedQuantity;
    private Long version;
    private Long createdOperationId;
    private Long lastOperationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
