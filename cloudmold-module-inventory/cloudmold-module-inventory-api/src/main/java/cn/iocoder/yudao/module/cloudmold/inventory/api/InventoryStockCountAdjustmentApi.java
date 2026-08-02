package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryStockCountAdjustmentApi {
    InventoryStockCountAdjustmentResult execute(InventoryStockCountAdjustmentCommand command);
}
