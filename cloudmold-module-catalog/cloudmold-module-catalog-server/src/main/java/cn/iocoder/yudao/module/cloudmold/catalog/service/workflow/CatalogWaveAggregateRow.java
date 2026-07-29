package cn.iocoder.yudao.module.cloudmold.catalog.service.workflow;

import lombok.Data;

@Data
public class CatalogWaveAggregateRow {

    private Integer styleCount;
    private Integer activeStyleCount;
    private Integer spuCount;
    private Integer activeSpuCount;
    private Integer skuCount;
    private Integer activeSkuCount;
    private String lastCatalogUpdatedAt;
}
