package cn.iocoder.yudao.module.cloudmold.warehouse.api;

/**
 * Applies an immutable Quality-owned disposition to Warehouse receipt fulfillment facts.
 */
public interface WarehouseProcurementQualityDecisionApi {

    WarehouseProcurementQualityDecisionResult execute(WarehouseProcurementQualityDecisionCommand command);
}
