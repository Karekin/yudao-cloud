package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.Data;

@Data
public class CatalogSkuProjectionView {

    private String canonicalStyleId;
    private String styleCode;
    private String styleName;
    private String canonicalSpuId;
    private String spuCode;
    private String productName;
    private String canonicalSkuId;
    private String skuCode;
    private String colorCode;
    private String colorName;
    private String sizeGroupCode;
    private String sizeCode;
    private String sizeName;
    private String primaryBarcode;
    private String baseUomCode;
    private String catalogStatus;
    private Long aggregateVersion;

}
