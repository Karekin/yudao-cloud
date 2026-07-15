package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseLocationDefinition {
    private String locationId;
    private String warehouseId;
    private String zoneId;
    private String locationCode;
    private String name;
    private String locationType;
    private String aisleCode;
    private String rackCode;
    private String bayCode;
    private String levelCode;
    private Boolean allowItemMixing;
    private Boolean allowLotMixing;
    private BigDecimal capacityQuantity;
    private String capacityUomCode;
    private String status;
    private Long expectedVersion;
}
