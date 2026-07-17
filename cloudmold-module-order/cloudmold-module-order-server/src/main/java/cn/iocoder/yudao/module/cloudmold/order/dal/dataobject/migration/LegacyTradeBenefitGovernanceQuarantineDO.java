package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitGovernanceQuarantineDO {
    private String quarantineGovernanceId;
    private Long tenantId;
    private String governanceRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String sourceCandidateEvidenceHash;
    private String sourceAssessmentStatus;
    private String sourceReasonCodes;
    private String decisionId;
    private String decisionStatus;
    private String recommendedAction;
    private String blockerCodes;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
