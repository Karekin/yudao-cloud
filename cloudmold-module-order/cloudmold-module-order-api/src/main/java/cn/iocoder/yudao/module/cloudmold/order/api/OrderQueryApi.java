package cn.iocoder.yudao.module.cloudmold.order.api;

public interface OrderQueryApi {
    OrderPaymentView requirePayableOrder(String orderId, Long amountMinor, String currencyCode);
    OrderFulfillmentView requireFulfillableOrder(String orderId);
}
