package cn.iocoder.yudao.module.cloudmold.aftersale.api;

public interface AfterSaleQueryApi {
    AfterSaleView get(String afterSaleId);
    AfterSaleView getByOrderItem(String orderId, String orderItemId);
}
