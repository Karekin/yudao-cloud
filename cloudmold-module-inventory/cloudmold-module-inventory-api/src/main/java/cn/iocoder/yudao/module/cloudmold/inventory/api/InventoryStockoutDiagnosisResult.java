package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockoutDiagnosisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String canonicalSpuId;
    private String spuCode;
    private String styleCode;
    private String warehouseId;
    private BigDecimal lowStockThreshold;
    private String outcomeCode;
    private Integer skuCount;
    private Integer stockoutCount;
    private Integer lowStockCount;
    private List<SizeStockFact> sizeStockFacts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SizeStockFact implements Serializable {

        private static final long serialVersionUID = 1L;

        private String canonicalSkuId;
        private String skuCode;
        private String colorCode;
        private String colorName;
        private String sizeCode;
        private String sizeName;
        private BigDecimal onHandQuantity;
        private BigDecimal reservedQuantity;
        private BigDecimal inTransitQuantity;
        private BigDecimal allocatableQuantity;
        private Long maxInventoryVersion;
        private String severity;

    }

}
