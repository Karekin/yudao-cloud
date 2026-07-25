package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogBarcodeRotateResult {
    private Long operationId;
    private String skuId;
    private String previousBarcodeId;
    private String previousBarcode;
    private String currentBarcodeId;
    private String currentBarcode;
    private String barcodeType;
    private Long aggregateVersion;
    private Boolean duplicate;
}
