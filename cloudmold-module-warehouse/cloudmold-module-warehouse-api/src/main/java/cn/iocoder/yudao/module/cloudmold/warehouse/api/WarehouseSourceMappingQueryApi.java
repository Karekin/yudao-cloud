package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import java.time.Instant;

public interface WarehouseSourceMappingQueryApi {
    WarehouseSourceMappingView resolveActive(WarehouseSourceReference source, Instant effectiveAt);
    WarehouseNetworkView resolveReadyNetwork(WarehouseSourceReference source, Instant effectiveAt);
}
