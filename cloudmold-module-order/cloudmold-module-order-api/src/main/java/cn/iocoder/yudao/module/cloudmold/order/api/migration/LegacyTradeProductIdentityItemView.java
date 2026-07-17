package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.util.List;

@Data
public class LegacyTradeProductIdentityItemView {
    private String identityItemId;
    private String identityRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private String itemEvidenceId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private Long legacySpuId;
    private Long legacySkuId;
    private Integer sourceParentCardinality;
    private String sourcePairStatus;
    private String currentReferenceStatus;
    private String currentProductSnapshotHash;
    private String qualificationId;
    private String historicalIdentityStatus;
    private List<String> blockerCodes;
    private Boolean identityAdmissionAllowed;
    private Boolean targetMappingAllowed;
    private String evidenceHash;
}
