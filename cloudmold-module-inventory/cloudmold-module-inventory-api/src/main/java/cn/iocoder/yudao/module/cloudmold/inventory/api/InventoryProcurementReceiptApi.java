package cn.iocoder.yudao.module.cloudmold.inventory.api;

/**
 * Sole Inventory write boundary for procurement receipt, quality disposition, and supplier return effects.
 */
public interface InventoryProcurementReceiptApi {
    InventoryProcurementReceiptResult execute(InventoryProcurementReceiptCommand command);
}
