package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

@Data
public class InventoryMigrationAssessmentResult {
    private Long operationId;
    private String migrationRunId;
    private String sourceSnapshotHash;
    private Integer candidateCount;
    private Integer eligibleCount;
    private Integer blockedCount;
    private Integer rejectedCount;
    private String status;
    private boolean duplicate;
}
