package cn.iocoder.yudao.module.cloudmold.catalog.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 SKU 条码项")
@Data
public class CatalogSkuBarcodeItem {

    @Schema(description = "条码 ID")
    private String barcodeId;
    @Schema(description = "条码值")
    private String barcode;
    @Schema(description = "条码类型")
    private String barcodeType;
    @Schema(description = "是否主用")
    private Boolean isPrimary;
    @Schema(description = "状态：10=生效，其他=非生效")
    private Integer status;
    @Schema(description = "生效开始")
    private LocalDateTime validFrom;
    @Schema(description = "生效结束")
    private LocalDateTime validTo;
}
