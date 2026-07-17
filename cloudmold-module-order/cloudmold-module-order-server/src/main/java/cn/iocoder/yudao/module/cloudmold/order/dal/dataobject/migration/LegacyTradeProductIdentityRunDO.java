package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeProductIdentityRunDO {
    private String identityRunId;
    private Long tenantId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private String evidenceRef;
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
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
