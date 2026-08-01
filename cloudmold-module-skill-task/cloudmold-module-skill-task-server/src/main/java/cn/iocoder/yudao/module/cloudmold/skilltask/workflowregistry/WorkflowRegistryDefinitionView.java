package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowRegistryDefinitionView(
        @JsonProperty("definition")
        JsonNode definition,
        @JsonProperty("attestation")
        WorkflowRegistryAttestationView attestation) {
}
