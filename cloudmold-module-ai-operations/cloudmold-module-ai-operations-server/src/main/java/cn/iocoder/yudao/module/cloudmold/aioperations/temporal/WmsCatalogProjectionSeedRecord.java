package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WmsCatalogProjectionSeedRecord {

    private String canonicalSkuId;
    private String skuCode;
    private String primaryBarcode;
    private String baseUomCode;
}
