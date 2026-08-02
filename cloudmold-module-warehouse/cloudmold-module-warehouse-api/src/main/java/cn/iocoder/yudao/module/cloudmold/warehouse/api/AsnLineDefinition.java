package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsnLineDefinition {
    private String asnLineId;
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
    private String tolerancePolicyVersion;
    private String tolerancePolicyHash;
}
