package cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception;

public enum FulfillmentExceptionAction {
    CONTACT_CARRIER,
    TRACK_AND_WAIT,
    REISSUE_SHIPMENT,
    FILE_CLAIM,
    REROUTE_ADDRESS,
    RETURN_TO_SENDER,
    MANUAL_REVIEW
}
