package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

public interface InventoryHealthSnapshotCommandApi {
    InventoryHealthSnapshotResult capture(InventoryHealthSnapshotCommand command, String actorPrincipalId);
}
