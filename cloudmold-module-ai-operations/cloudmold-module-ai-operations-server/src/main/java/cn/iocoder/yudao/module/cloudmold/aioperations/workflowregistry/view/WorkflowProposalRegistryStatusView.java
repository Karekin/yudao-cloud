package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record WorkflowProposalRegistryStatusView(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("stable_version_id") String stableVersionId,
        @JsonProperty("stable_version") String stableVersion,
        @JsonProperty("candidate_version_id") String candidateVersionId,
        @JsonProperty("candidate_version") String candidateVersion,
        @JsonProperty("candidate_status") String candidateStatus,
        @JsonProperty("pointer_version") Long pointerVersion,
        boolean duplicate) {
}
