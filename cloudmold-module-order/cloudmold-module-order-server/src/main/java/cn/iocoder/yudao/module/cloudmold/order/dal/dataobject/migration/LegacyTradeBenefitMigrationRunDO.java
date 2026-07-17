package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitMigrationRunDO {
    private String migrationRunId;
    private Long tenantId;
    private String sourceScope;
    private String policyVersion;
    private String evidenceRef;
    private String sourceSnapshotHash;
    private LocalDateTime sourceWatermark;
    private Integer sourceOrderCount;
    private Integer nonDeletedOrderCount;
    private Integer deletedExcludedCount;
    private Integer noBenefitOrderCount;
    private Integer benefitEvidencePendingOrderCount;
    private Integer quarantinedOrderCount;
    private Integer benefitComponentCount;
    private Long sourceBenefitAmountMinor;
    private Long componentAmountMinor;
    private Integer sourceItemCount;
    private Integer activeItemCount;
    private Integer excludedItemCount;
    private String itemEvidenceHash;
    private Long itemEvidenceBenefitAmountMinor;
    private Boolean itemEvidenceComplete;
    private Integer productSnapshotCapturedItemCount;
    private Integer productSnapshotIncompleteItemCount;
    private String productSnapshotEvidenceHash;
    private Boolean productSnapshotEvidenceComplete;
    private Integer unresolvedIdentityCount;
    private Integer unresolvedFundingCount;
    private Integer importAllowedComponentCount;
    private Boolean productionMigrationEnabled;
    private String status;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
