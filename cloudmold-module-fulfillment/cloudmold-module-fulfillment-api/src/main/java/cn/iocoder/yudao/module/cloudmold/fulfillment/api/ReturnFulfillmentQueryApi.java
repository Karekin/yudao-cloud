package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

public interface ReturnFulfillmentQueryApi {
    ReturnFulfillmentView getForAfterSale(String afterSaleId, String returnFulfillmentId);
    ReturnFulfillmentView requireInspectionAccepted(String afterSaleId, String returnFulfillmentId);
}
