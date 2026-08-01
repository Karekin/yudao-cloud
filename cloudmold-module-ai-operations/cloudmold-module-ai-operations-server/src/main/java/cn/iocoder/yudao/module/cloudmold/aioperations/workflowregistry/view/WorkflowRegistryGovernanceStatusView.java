package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record WorkflowRegistryGovernanceStatusView(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("stable_version_id") String stableVersionId,
        @JsonProperty("candidate_version_id") String candidateVersionId,
        @JsonProperty("pointer_version") Long pointerVersion,
        @JsonProperty("kill_switch_enabled") boolean killSwitchEnabled,
        @JsonProperty("current_status") String currentStatus,
        @JsonProperty("validation_request_id") String validationRequestId,
        @JsonProperty("validation_challenge") String validationChallenge,
        @JsonProperty("evaluation_id") String evaluationId,
        @JsonProperty("approval_id") String approvalId,
        @JsonProperty("release_id") String releaseId,
        boolean duplicate) {
}
