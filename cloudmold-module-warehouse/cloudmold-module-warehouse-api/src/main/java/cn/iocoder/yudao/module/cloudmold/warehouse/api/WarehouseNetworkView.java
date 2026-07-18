package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseNetworkView {
    private String mappingId;
    private String warehouseId;
    private String warehouseStatus;
    private String zoneId;
    private String zoneStatus;
    private String locationId;
    private String locationStatus;
}
