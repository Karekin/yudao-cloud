package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

@Data
public class InventoryLotResult {
    private Long operationId;
    private String lotId;
    private String lotStatus;
    private Long lotVersion;
    private String mappingId;
    private String mappingStatus;
    private Long mappingVersion;
    private String eventId;
    private boolean duplicate;
}
