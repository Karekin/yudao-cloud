package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;

/**
 * Minimal typed reads needed to validate legacy master-data references before
 * canonical Merchant/Warehouse workflows are allowed to write.
 */
public interface YudaoLegacyMasterDataQueryApi {

    LegacyWarehouseView getErpWarehouse(Long warehouseId);

    record LegacyWarehouseView(Long warehouseId, String name, String address,
                               Integer status) implements Serializable {
    }
}
