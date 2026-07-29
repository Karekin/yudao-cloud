package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnFulfillmentView {
    private Long operationId;
    private String returnFulfillmentId;
    private String returnFulfillmentNo;
    private String returnShipmentId;
    private String inspectionId;
    private String afterSaleId;
    private String afterSaleItemId;
    private String orderId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private String carrierCode;
    private String waybillNo;
    private String qualityStatus;
    private String dispositionAssessmentId;
    private String dispositionCode;
    private String conditionGrade;
    private String inspectionEvidenceRef;
    private String currentStatus;
    private Long aggregateVersion;
    private Boolean duplicate;
}
