package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WorkflowProposalRegistryStatusView(
        @JsonProperty("workflow_id") String workflowId,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("stable_version_id") String stableVersionId,
        @JsonProperty("stable_version") String stableVersion,
        @JsonProperty("stable_status") String stableStatus,
        @JsonProperty("candidate_version_id") String candidateVersionId,
        @JsonProperty("candidate_version") String candidateVersion,
        @JsonProperty("candidate_status") String candidateStatus,
        @JsonProperty("definition_sha256") String definitionSha256,
        @JsonProperty("base_sha256") String baseSha256,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("proposed_by") String proposedBy,
        @JsonProperty("created_time") LocalDateTime createdTime,
        @JsonProperty("updated_time") LocalDateTime updatedTime,
        @JsonProperty("pointer_version") Long pointerVersion,
        boolean duplicate) {
}
