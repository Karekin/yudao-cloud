package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeTargetReadinessResult {
    private Long operationId;
    private String targetReadinessRunId;
    private String sourceMigrationRunId;
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
    private Boolean duplicate;
}
