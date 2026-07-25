package cn.iocoder.yudao.module.cloudmold.order.api;

public interface AppOrderQueryApi {
    AppOrderView requireOwned(String buyerPrincipalId, String orderId);
    AppOrderPageView listOwned(String buyerPrincipalId, String status, int pageNo, int pageSize);
}
