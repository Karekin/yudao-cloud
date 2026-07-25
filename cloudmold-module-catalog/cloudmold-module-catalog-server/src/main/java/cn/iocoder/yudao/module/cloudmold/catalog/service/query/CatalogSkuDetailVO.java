package cn.iocoder.yudao.module.cloudmold.catalog.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CloudMold 规范 SKU 详情")
@Data
public class CatalogSkuDetailVO {

    @Schema(description = "规范款式 ID")
    private String canonicalStyleId;
    @Schema(description = "款式编码")
    private String styleCode;
    @Schema(description = "款式名称")
    private String styleName;
    @Schema(description = "规范 SPU ID")
    private String canonicalSpuId;
    @Schema(description = "SPU 编码")
    private String spuCode;
    @Schema(description = "商品名称")
    private String productName;
    @Schema(description = "规范 SKU ID")
    private String canonicalSkuId;
    @Schema(description = "SKU 编码")
    private String skuCode;
    @Schema(description = "规格键")
    private String variantKey;
    @Schema(description = "规格键哈希")
    private String variantKeyHash;
    @Schema(description = "颜色 ID")
    private String colorId;
    @Schema(description = "颜色编码")
    private String colorCode;
    @Schema(description = "颜色名称")
    private String colorName;
    @Schema(description = "尺码 ID")
    private String sizeId;
    @Schema(description = "尺码编码")
    private String sizeCode;
    @Schema(description = "尺码名称")
    private String sizeName;
    @Schema(description = "尺码组编码")
    private String sizeGroupCode;
    @Schema(description = "尺码组 ID")
    private String sizeGroupId;
    @Schema(description = "款式生命周期状态")
    private Integer styleStatus;
    @Schema(description = "款式聚合版本")
    private Long styleVersion;
    @Schema(description = "SPU 生命周期状态")
    private Integer spuStatus;
    @Schema(description = "SPU 聚合版本")
    private Long spuVersion;
    @Schema(description = "颜色生命周期状态")
    private Integer colorStatus;
    @Schema(description = "颜色聚合版本")
    private Long colorVersion;
    @Schema(description = "尺码组生命周期状态")
    private Integer sizeGroupStatus;
    @Schema(description = "尺码组聚合版本")
    private Long sizeGroupVersion;
    @Schema(description = "尺码生命周期状态")
    private Integer sizeStatus;
    @Schema(description = "尺码聚合版本")
    private Long sizeVersion;
    @Schema(description = "主条码（当前生效且标记为主用的条码）")
    private String primaryBarcode;
    @Schema(description = "基础计量单位编码")
    private String baseUomCode;
    @Schema(description = "生命周期状态：0=草稿 10=生效 20=停用 90=归档")
    private Integer catalogStatus;
    @Schema(description = "聚合版本")
    private Long aggregateVersion;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    @Schema(description = "条码列表")
    private List<CatalogSkuBarcodeItem> barcodes;
}
