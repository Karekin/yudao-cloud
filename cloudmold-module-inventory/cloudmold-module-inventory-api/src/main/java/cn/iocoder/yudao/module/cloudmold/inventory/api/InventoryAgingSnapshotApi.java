package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryAgingSnapshotApi {

    InventoryAgingSnapshotResult capture(InventoryAgingSnapshotCommand command);

}
