package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefineCatalogSkuCommand {

    private String idempotencyKey;
    private String styleCode;
    private String styleName;
    private String planningCategoryRef;
    private String brandRef;
    private Integer planningYear;
    private String seasonCode;
    private String waveCode;
    private String spuCode;
    private String productName;
    private String salesCategoryRef;
    private String skuCode;
    private String barcode;
    private String barcodeType;
    private String colorCode;
    private String colorName;
    private String sizeGroupCode;
    private String sizeGroupName;
    private String sizeCode;
    private String sizeName;
    private Integer sizeSort;
    private String baseUomCode;
    private String status;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;

}
