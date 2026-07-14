package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface FulfillmentCommandApi {
    FulfillmentCommandResult execute(FulfillmentCommand command);
}
