package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationShadowRoundDO {
    private String roundId;
    private Long tenantId;
    private String windowId;
    private Integer roundNumber;
    private LocalDateTime observedAt;
    private String previousSourceWatermarkValue;
    private String previousSourceWatermarkHash;
    private String sourceWatermarkKind;
    private String sourceWatermarkValue;
    private String sourceWatermarkHash;
    private LocalDateTime sourceWatermarkCapturedAt;
    private Boolean sourceMonotonic;
    private String previousTargetWatermarkValue;
    private String previousTargetWatermarkHash;
    private String targetWatermarkKind;
    private String targetWatermarkValue;
    private String targetWatermarkHash;
    private LocalDateTime targetWatermarkAppliedAt;
    private Boolean targetMonotonic;
    private Boolean targetContainsSource;
    private String watermarkValidator;
    private String watermarkValidationEvidenceRef;
    private Boolean watermarkValid;
    private Integer watermarkLagSeconds;
    private Integer roundGapSeconds;
    private String denominatorHash;
    private Integer expectedItemCount;
    private Integer matchCount;
    private Integer differentCount;
    private Integer uncomparableCount;
    private Long collectorId;
    private String evidenceRef;
    private String status;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
}
