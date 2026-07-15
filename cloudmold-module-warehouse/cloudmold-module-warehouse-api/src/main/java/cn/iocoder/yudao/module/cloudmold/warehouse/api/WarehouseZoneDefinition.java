package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseZoneDefinition {
    private String zoneId;
    private String warehouseId;
    private String zoneCode;
    private String name;
    private String zoneType;
    private String status;
    private Long expectedVersion;
}
