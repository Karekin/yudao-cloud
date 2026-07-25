package cn.iocoder.yudao.module.cloudmold.inventory.api;

import java.time.Instant;
import java.util.List;

public interface InventoryV3AvailabilityQueryApi {
    List<InventoryV3AvailabilityView> listByLot(String lotId, Instant eligibilityAt);

    InventorySkuAvailabilityView getBySku(String canonicalSkuId, Instant eligibilityAt);
}
