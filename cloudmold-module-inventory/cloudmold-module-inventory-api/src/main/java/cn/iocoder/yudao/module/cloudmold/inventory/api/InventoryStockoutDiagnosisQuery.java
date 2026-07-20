package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStockoutDiagnosisQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    private String canonicalSpuId;
    private String warehouseId;
    private BigDecimal lowStockThreshold;

}
