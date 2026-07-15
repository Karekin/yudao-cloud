package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationRunDO {
    private String migrationRunId;
    private Long tenantId;
    private String sourceScope;
    private String policyVersion;
    private String evidenceRef;
    private String sourceSnapshotHash;
    private Integer candidateCount;
    private Integer eligibleCount;
    private Integer blockedCount;
    private Integer rejectedCount;
    private String status;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
