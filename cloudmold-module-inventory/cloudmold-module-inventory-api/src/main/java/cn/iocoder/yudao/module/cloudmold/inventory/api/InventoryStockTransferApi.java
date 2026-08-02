package cn.iocoder.yudao.module.cloudmold.inventory.api;

/**
 * Sole public write boundary for canonical warehouse-to-warehouse stock transfers.
 */
public interface InventoryStockTransferApi {
    InventoryStockTransferResult execute(InventoryStockTransferCommand command);
}
