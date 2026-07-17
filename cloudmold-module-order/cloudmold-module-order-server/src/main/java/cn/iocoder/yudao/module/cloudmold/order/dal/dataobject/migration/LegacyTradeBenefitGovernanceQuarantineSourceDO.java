package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitGovernanceQuarantineSourceDO {
    private Long tenantId;
    private String sourceMigrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String legacySnapshotHash;
    private String assessmentStatus;
    private String reasonCodes;
    private Integer decisionCount;
    private String decisionId;
    private String decisionSourceCandidateEvidenceHash;
}
