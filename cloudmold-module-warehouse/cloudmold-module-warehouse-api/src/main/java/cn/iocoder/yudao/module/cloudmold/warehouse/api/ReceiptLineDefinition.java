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
public class ReceiptLineDefinition {
    private String receiptLineId;
    private Integer lineNo;
    private String asnLineId;
    private String procurementOrderId;
    private String procurementOrderItemId;
    private String deliveryScheduleId;
    private Long poReleaseVersion;
    private Long expectedFulfillmentVersion;
    private String supplierId;
    private String warehouseId;
    private String receiptLocationId;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String baseUomCode;
    private BigDecimal receivedQuantity;
    private String lotId;
    private String qualityStatus;
    private String qualityInspectionId;
}
