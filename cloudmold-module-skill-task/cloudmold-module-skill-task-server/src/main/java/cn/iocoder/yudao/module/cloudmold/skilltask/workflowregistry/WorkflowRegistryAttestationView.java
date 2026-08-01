package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkflowRegistryAttestationView(
        @JsonProperty("issuer")
        String issuer,
        @JsonProperty("workflow_id")
        String workflowId,
        @JsonProperty("owner_user_id")
        String ownerUserId,
        @JsonProperty("version_id")
        String versionId,
        @JsonProperty("definition_sha256")
        String definitionSha256,
        @JsonProperty("issued_at")
        long issuedAt,
        @JsonProperty("signature")
        String signature) {
}
