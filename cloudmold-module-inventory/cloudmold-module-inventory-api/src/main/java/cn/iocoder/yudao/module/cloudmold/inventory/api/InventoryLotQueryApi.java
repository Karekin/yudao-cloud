package cn.iocoder.yudao.module.cloudmold.inventory.api;

import java.time.Instant;

public interface InventoryLotQueryApi {
    InventoryLotView requireCurrent(String lotId, Instant eligibilityAt);

    InventoryLotView requireBySource(String sourceSystem, String sourceType, String sourceId,
                                     Instant effectiveAt, Instant eligibilityAt);
}
