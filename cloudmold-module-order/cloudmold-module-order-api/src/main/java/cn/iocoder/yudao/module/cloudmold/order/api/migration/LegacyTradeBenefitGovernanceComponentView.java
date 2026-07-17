package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class LegacyTradeBenefitGovernanceComponentView {
    private String componentGovernanceId;
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
    private Instant observedSourceCreatedAt;
    private Instant observedSourceUpdatedAt;
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
    private List<String> blockerCodes;
    private Boolean governanceAdmissionAllowed;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
}
