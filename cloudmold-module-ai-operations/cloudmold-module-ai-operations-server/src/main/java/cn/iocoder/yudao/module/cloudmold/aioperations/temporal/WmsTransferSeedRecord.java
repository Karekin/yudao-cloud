package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WmsTransferSeedRecord {

    private Long sourceWarehouseId;
    private Long targetWarehouseId;
    private Long wmsSkuId;
    private Long itemId;
    private String wmsSkuCode;
    private String wmsBarcode;
    private String itemUnit;
    private String targetWarehouseMappingId;
    private String canonicalWarehouseId;
    private String canonicalSkuId;
    private String catalogSkuCode;
    private String catalogBarcode;
    private String baseUomCode;
}
