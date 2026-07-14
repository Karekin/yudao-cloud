package cn.iocoder.yudao.module.cloudmold.order.api;

public enum OrderOperation {
    PLACE,
    PLACE_FROM_LISTING,
    CONFIRM_INVENTORY,
    CONFIRM_PAYMENT,
    SHIP,
    SHIP_WITH_FULFILLMENT,
    COMPLETE,
    COMPLETE_AFTER_DELIVERY,
    CANCEL,
    REQUEST_CANCELLATION,
    FINALIZE_CANCELLATION,
    CONFIRM_REFUND,
    RETURN
}
