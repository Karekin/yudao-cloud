package cn.iocoder.yudao.module.cloudmold.catalog.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 SKU 分页项")
@Data
public class CatalogSkuPageItem {

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
    private Integer catalogStatus;
    private Long aggregateVersion;
    private LocalDateTime updatedAt;
}
