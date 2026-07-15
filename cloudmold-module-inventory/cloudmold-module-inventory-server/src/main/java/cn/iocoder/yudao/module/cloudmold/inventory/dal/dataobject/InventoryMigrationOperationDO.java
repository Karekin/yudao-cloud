package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationOperationDO {
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String commandType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String migrationRunId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
