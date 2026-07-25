package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface AppFulfillmentQueryApi {
    AppFulfillmentView getByOrder(String orderId);
}
