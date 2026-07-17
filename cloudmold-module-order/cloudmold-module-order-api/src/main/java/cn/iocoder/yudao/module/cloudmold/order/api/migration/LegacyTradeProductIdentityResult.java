package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeProductIdentityResult {
    private Long operationId;
    private String identityRunId;
    private String sourceMigrationRunId;
    private String governanceEvidenceHash;
    private Integer sourceItemCount;
    private Integer activeItemCount;
    private Integer excludedItemCount;
    private Integer sourcePairUnambiguousCount;
    private Integer sourceParentConflictItemCount;
    private Integer currentRelationObservedCount;
    private Integer historicalIdentityQualifiedCount;
    private Integer identityAdmittedItemCount;
    private Boolean targetMappingEnabled;
    private String status;
    private Boolean duplicate;
}
