package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationPilotCheckpointDO {
    private String checkpointId;
    private Long tenantId;
    private String batchId;
    private String checkpointType;
    private Long actorId;
    private Long batchVersion;
    private String scopeHash;
    private String policyHash;
    private Integer itemCount;
    private String detailsJson;
    private LocalDateTime createdAt;
}
