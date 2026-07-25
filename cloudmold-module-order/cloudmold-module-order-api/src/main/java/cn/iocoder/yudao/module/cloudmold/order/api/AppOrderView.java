package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppOrderView {
    private String orderId;
    private String orderNo;
    private String buyerPrincipalId;
    private String status;
    private Long aggregateVersion;
    private BigDecimal totalQuantity;
    private Long productAmountMinor;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private Long payableAmountMinor;
    private String currencyCode;
    private String paymentId;
    private String paymentStatus;
    private String fulfillmentId;
    private String fulfillmentStatus;
    private String shipmentId;
    private List<OrderLineView> items;
}
