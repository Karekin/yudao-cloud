package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefineCatalogSkuResult {

    private Long operationId;
    private String canonicalStyleId;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private String colorId;
    private String sizeGroupId;
    private String sizeId;
    private String primaryBarcodeId;
    private String styleStatus;
    private Long styleVersion;
    private String spuStatus;
    private Long spuVersion;
    private String colorStatus;
    private Long colorVersion;
    private String sizeGroupStatus;
    private Long sizeGroupVersion;
    private String sizeStatus;
    private Long sizeVersion;
    private String skuStatus;
    private Long aggregateVersion;
    private Boolean created;
    private Boolean duplicate;

}
