package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryStockCountSnapshotApi {
    InventoryStockCountSnapshotView requireSnapshot(InventoryStockCountSnapshotQuery query);
}
