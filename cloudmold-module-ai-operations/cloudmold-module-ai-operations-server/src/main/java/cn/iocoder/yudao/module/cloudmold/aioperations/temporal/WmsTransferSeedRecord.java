package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WmsTransferSeedRecord {

    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Long wmsSkuId;
}
