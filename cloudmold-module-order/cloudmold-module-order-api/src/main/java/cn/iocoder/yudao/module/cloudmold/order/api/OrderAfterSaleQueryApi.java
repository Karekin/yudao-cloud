package cn.iocoder.yudao.module.cloudmold.order.api;

public interface OrderAfterSaleQueryApi {
    OrderAfterSaleView requireEligible(String orderId, String orderItemId);
}
