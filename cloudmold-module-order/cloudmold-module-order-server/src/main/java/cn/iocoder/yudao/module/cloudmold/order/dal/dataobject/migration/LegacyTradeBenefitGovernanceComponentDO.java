package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitGovernanceComponentDO {
    private String componentGovernanceId;
    private Long tenantId;
    private String governanceRunId;
    private String sourceMigrationRunId;
    private String componentId;
    private String candidateId;
    private Long legacyOrderId;
    private String componentType;
    private Long componentAmountMinor;
    private String sourceReference;
    private String sourceComponentEvidenceHash;
    private String currentReferenceStatus;
    private String observedSourceTable;
    private Long observedSourceId;
    private LocalDateTime observedSourceCreatedAt;
    private LocalDateTime observedSourceUpdatedAt;
    private String observedSourceStatus;
    private Boolean observedSourceDeleted;
    private Long observedSourceSpuId;
    private String currentReferenceSnapshotHash;
    private String identityQualificationId;
    private String historicalIdentityStatus;
    private Integer fundingShareCount;
    private Long fundingAmountMinor;
    private String fundingResolutionStatus;
    private String governanceStatus;
    private String blockerCodes;
    private Boolean governanceAdmissionAllowed;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
