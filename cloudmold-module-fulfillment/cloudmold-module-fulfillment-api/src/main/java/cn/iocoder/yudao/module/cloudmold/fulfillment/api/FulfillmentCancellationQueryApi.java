package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface FulfillmentCancellationQueryApi {
    FulfillmentCommandResult requireCreatedByOrder(String orderId);
    FulfillmentCommandResult requireCancelled(String orderId, String fulfillmentId, String cancellationSagaId);
}
