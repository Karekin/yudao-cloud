package cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception;

public interface FulfillmentExceptionQueryApi {
    FulfillmentExceptionView get(String exceptionId);

    FulfillmentExceptionView getLatestByOrder(String orderId);
}
