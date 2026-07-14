package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface ForwardFulfillmentAfterSaleQueryApi {
    ForwardFulfillmentAfterSaleView requireDelivered(String orderId, String fulfillmentId, String shipmentId,
                                                      String orderItemId);
}
