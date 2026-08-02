package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public enum StockCountOperation {
    CREATE_DRAFT,
    SUBMIT,
    START_COUNTING,
    RECORD_COUNT_BATCH,
    APPROVE_DIFFERENCE,
    ADJUST,
    COMPLETE,
    CANCEL
}
