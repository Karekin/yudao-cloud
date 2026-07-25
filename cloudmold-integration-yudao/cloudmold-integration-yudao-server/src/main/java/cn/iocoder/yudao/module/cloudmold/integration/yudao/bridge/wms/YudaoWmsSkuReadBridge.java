package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.wms;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsSkuReadPort;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class YudaoWmsSkuReadBridge implements LegacyWmsSkuReadPort {

    private final WmsItemService wmsItemService;
    private final WmsItemSkuService wmsItemSkuService;

    @Override
    public WmsSkuSnapshot getSku(Long wmsSkuId) {
        if (wmsSkuId == null || wmsSkuId <= 0) {
            return null;
        }
        List<WmsItemSkuDO> matches = wmsItemSkuService.getItemSkuListByIds(List.of(wmsSkuId));
        if (matches == null || matches.size() != 1) {
            return null;
        }
        WmsItemSkuDO sku = matches.get(0);
        WmsItemDO item = wmsItemService.getItem(sku.getItemId());
        if (item == null) {
            return null;
        }
        return new WmsSkuSnapshot(
                sku.getId(),
                sku.getItemId(),
                sku.getCode(),
                sku.getBarCode(),
                item.getUnit());
    }
}
