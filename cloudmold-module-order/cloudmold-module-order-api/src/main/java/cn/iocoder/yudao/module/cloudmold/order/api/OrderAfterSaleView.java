package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAfterSaleView {
    private String orderId;
    private String orderNo;
    private String buyerId;
    private String status;
    private Long aggregateVersion;
    private Long payableAmountMinor;
    private String currencyCode;
    private String paymentId;
    private String fulfillmentId;
    private String shipmentId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Long lineAmountMinor;
    private String reservationId;
    private String listingId;
    private String listingOfferId;
}
