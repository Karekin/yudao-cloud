package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MerchantManagedAdmissionWorkflowResult {
    public enum Status { PREPARE, WAITING, RUNNING, SUCCEEDED, FAILED }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String type;
        private String id;
        private String status;
        private Long version;
        private String label;
    }

    private String workflowType;
    private String workflowInstanceKey;
    private String businessKey;
    private Status status;
    private String phase;
    private Boolean terminal;
    private Boolean actionRequired;
    private String summary;
    private Long aggregateVersion;
    private String invitationId;
    private String invitationCode;
    private String invitationStatus;
    private Long invitationVersion;
    private String buyerAssignmentId;
    private String buyerAssignmentStatus;
    private Long buyerAssignmentVersion;
    private String buyerTlPrincipalId;
    private String buyerPrincipalId;
    private String gradeDecisionId;
    private String gradeCode;
    private String gradeDecisionStatus;
    private Long gradeDecisionVersion;
    private String probationAssessmentId;
    private String probationAssessmentStatus;
    private Long probationAssessmentVersion;
    private String scorecardId;
    private String scorecardMonth;
    private String scorecardStatus;
    private Long scorecardVersion;
    private String exitDecisionId;
    private String exitReasonType;
    private String exitDecisionStatus;
    private Long exitDecisionVersion;
    private List<String> blockers;
    private List<String> nextActions;
    private List<Artifact> artifacts;
}
