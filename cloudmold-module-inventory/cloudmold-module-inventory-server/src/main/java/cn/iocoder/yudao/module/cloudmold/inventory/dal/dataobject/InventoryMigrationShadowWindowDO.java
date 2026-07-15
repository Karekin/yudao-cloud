package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationShadowWindowDO {
    private String windowId;
    private Long tenantId;
    private String batchId;
    private String migrationRunId;
    private String environment;
    private String environmentFingerprint;
    private String manifestHash;
    private String expectedItemSetHash;
    private String admissionCheckpointId;
    private String admissionCheckpointHash;
    private String admissionEventId;
    private Long admissionBatchVersion;
    private String policyVersion;
    private String policyHash;
    private String targetProjectionKind;
    private Integer targetProjectionVersion;
    private Boolean targetMaterialized;
    private Integer expectedItemCount;
    private Integer requiredRoundCount;
    private Integer minimumDurationSeconds;
    private Integer maxRoundIntervalSeconds;
    private Integer maxWatermarkLagSeconds;
    private Long collectorId;
    private Long verifierId;
    private String status;
    private String verificationResult;
    private Long version;
    private Long aggregateVersion;
    private Integer observedRoundCount;
    private Integer totalMatchCount;
    private Integer totalDifferentCount;
    private Integer totalUncomparableCount;
    private String lastSourceWatermarkKind;
    private String lastSourceWatermarkValue;
    private String lastSourceWatermarkHash;
    private LocalDateTime lastSourceWatermarkCapturedAt;
    private String lastTargetWatermarkKind;
    private String lastTargetWatermarkValue;
    private String lastTargetWatermarkHash;
    private LocalDateTime lastTargetWatermarkAppliedAt;
    private LocalDateTime startedAt;
    private LocalDateTime lastObservedAt;
    private LocalDateTime finalizedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
