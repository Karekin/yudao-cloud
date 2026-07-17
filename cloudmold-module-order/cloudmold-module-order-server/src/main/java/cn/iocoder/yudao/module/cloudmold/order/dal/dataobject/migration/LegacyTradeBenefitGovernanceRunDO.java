package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitGovernanceRunDO {
    private String governanceRunId;
    private Long tenantId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private String evidenceRef;
    private String governanceEvidenceHash;
    private Integer sourceComponentCount;
    private Integer sourceReferencePresentCount;
    private Integer currentReferenceObservedCount;
    private Integer historicalIdentityQualifiedCount;
    private Integer identityBlockedCount;
    private Integer fundingQualifiedCount;
    private Integer fundingBlockedCount;
    private Integer sourceQuarantineCount;
    private Integer quarantineDecidedCount;
    private Integer quarantineOpenCount;
    private Integer governanceAdmittedComponentCount;
    private Boolean productionMigrationEnabled;
    private String status;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
