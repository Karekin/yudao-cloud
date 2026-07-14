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
public class ForwardFulfillmentAfterSaleView {
    private String fulfillmentId;
    private String shipmentId;
    private String orderId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String reservationId;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private String status;
    private Long aggregateVersion;
}
