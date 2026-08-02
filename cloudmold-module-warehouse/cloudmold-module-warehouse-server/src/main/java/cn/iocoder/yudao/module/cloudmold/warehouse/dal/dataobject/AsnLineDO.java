package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_procurement_asn_line")
public class AsnLineDO {
    private String asnLineId;
    private Long tenantId;
    private String asnId;
    private Integer lineNo;
    private String procurementOrderId;
    private String procurementOrderItemId;
    private String deliveryScheduleId;
    private Long poReleaseVersion;
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
    private Long fulfillmentVersion;
    private String valuationPolicyId;
    private String valuationPolicyVersion;
    private String valuationPolicyHash;
    private Long unitCostAmountMinor;
    private String currencyCode;
    private String roundingPolicyCode;
    private String tolerancePolicyVersion;
    private String tolerancePolicyHash;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
