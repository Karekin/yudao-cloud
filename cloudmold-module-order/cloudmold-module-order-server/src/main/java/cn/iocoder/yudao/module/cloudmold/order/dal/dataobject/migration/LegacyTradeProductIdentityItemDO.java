package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeProductIdentityItemDO {
    private String identityItemId;
    private Long tenantId;
    private String identityRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private String itemEvidenceId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private Long legacySpuId;
    private Long legacySkuId;
    private String sourceItemEvidenceHash;
    private Integer sourceParentCardinality;
    private String sourcePairStatus;
    private String currentReferenceStatus;
    private String currentProductSnapshotHash;
    private String qualificationId;
    private String historicalIdentityStatus;
    private String blockerCodes;
    private Boolean identityAdmissionAllowed;
    private Boolean targetMappingAllowed;
    private String evidenceHash;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
