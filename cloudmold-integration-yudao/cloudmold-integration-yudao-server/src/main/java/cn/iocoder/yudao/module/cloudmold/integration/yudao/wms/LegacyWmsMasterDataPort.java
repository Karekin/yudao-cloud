package cn.iocoder.yudao.module.cloudmold.integration.yudao.wms;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;

/**
 * Keeps legacy WMS master-data mutations behind the same anti-corruption
 * boundary as physical operations without exposing upstream controller types.
 */
public interface LegacyWmsMasterDataPort {

    Long createMerchant(YudaoWmsCommandApi.MerchantCommand command);

    Long createWarehouse(YudaoWmsCommandApi.WarehouseCommand command);

    Long createItemCategory(YudaoWmsCommandApi.ItemCategoryCommand command);

    Long createItem(YudaoWmsCommandApi.ItemCommand command);
}
