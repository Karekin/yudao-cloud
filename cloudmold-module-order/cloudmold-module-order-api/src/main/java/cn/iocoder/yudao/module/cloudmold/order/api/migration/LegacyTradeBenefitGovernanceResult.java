package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitGovernanceResult {
    private Long operationId;
    private String governanceRunId;
    private String sourceMigrationRunId;
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
    private Boolean duplicate;
}
