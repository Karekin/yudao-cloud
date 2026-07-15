package cn.iocoder.yudao.module.cloudmold.warehouse.api;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseNetworkCommandResult {
    private Long operationId;
    private String warehouseId;
    private String zoneId;
    private String locationId;
    private String mappingId;
    private String assignmentId;
    private Long aggregateVersion;
    private String status;
    private boolean duplicate;
}
