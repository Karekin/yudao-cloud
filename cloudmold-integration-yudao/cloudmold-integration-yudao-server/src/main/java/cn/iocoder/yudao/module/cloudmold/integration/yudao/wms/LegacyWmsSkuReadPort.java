package cn.iocoder.yudao.module.cloudmold.integration.yudao.wms;

import java.io.Serializable;

/**
 * CloudMold-owned read boundary for the upstream WMS item/SKU model.
 */
public interface LegacyWmsSkuReadPort {

    WmsSkuSnapshot getSku(Long wmsSkuId);

    record WmsSkuSnapshot(Long skuId,
                          Long itemId,
                          String skuCode,
                          String primaryBarcode,
                          String baseUomCode) implements Serializable {
    }
}
