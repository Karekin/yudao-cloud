package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface FulfillmentShipmentValidationApi {
    FulfillmentCommandResult requireShipped(String orderId, String fulfillmentId, String shipmentId);
    FulfillmentCommandResult requireDelivered(String orderId, String fulfillmentId);
}
