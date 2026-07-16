package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitAssessmentResult {
    private Long operationId;
    private String migrationRunId;
    private String sourceSnapshotHash;
    private Integer sourceOrderCount;
    private Integer nonDeletedOrderCount;
    private Integer deletedExcludedCount;
    private Integer noBenefitOrderCount;
    private Integer benefitEvidencePendingOrderCount;
    private Integer quarantinedOrderCount;
    private Integer benefitComponentCount;
    private Long sourceBenefitAmountMinor;
    private Long componentAmountMinor;
    private Integer unresolvedIdentityCount;
    private Integer unresolvedFundingCount;
    private Integer importAllowedComponentCount;
    private Boolean productionMigrationEnabled;
    private String status;
    private Boolean duplicate;
}
