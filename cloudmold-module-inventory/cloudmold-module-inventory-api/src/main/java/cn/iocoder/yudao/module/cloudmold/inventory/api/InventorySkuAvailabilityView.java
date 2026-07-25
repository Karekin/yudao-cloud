package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySkuAvailabilityView {
    private String canonicalSkuId;
    private BigDecimal allocatableQuantity;
    private String baseUomCode;
    private Long inventoryVersion;
    private Integer balanceCount;
    private Integer uomCount;
}
