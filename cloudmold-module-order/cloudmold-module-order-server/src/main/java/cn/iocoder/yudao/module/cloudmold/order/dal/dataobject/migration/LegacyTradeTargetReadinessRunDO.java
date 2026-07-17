package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeTargetReadinessRunDO {
    private String targetReadinessRunId;
    private Long tenantId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private String evidenceRef;
    private String targetMappingEvidenceHash;
    private Integer sourceOrderCount;
    private Integer activeOrderCount;
    private Integer excludedOrderCount;
    private Integer sourceItemCount;
    private Integer activeItemCount;
    private Integer excludedItemCount;
    private Integer buyerResolvedOrderCount;
    private Integer orderMappingQualifiedCount;
    private Integer lifecycleMappingQualifiedCount;
    private Integer fullyMappedItemCount;
    private Integer mappingAdmittedOrderCount;
    private Integer mappingBlockedOrderCount;
    private Integer canonicalImportAllowedOrderCount;
    private Boolean productionMigrationEnabled;
    private String status;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
