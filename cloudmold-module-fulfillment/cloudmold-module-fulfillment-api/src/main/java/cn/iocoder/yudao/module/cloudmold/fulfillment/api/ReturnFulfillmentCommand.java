package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

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
public class ReturnFulfillmentCommand {
    private ReturnFulfillmentOperation operation;
    private String idempotencyKey;
    private String runId;
    private String returnFulfillmentId;
    private Long expectedVersion;
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
    private String operatorId;
    private String qualityStatus;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
