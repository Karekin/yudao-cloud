package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.util.List;

@Data
public class LegacyTradeBenefitGovernanceQuarantineView {
    private String quarantineGovernanceId;
    private String governanceRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String sourceCandidateEvidenceHash;
    private String sourceAssessmentStatus;
    private List<String> sourceReasonCodes;
    private String decisionId;
    private String decisionStatus;
    private String recommendedAction;
    private List<String> blockerCodes;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
}
