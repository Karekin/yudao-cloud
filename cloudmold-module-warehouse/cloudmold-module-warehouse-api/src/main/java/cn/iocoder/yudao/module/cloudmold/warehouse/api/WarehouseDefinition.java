package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseDefinition {
    private String warehouseId;
    private String warehouseCode;
    private String name;
    private String warehouseType;
    private String timezone;
    private String status;
    private Long expectedVersion;
}
