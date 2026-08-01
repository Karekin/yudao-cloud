package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowProposalRegistryRequest {

    @NotNull
    @Min(0)
    @JsonProperty("expected_pointer_version")
    private Long expectedPointerVersion;

    @NotNull
    private JsonNode proposal;

    @NotNull
    @JsonProperty("base_definition")
    private JsonNode baseDefinition;

    @NotNull
    @JsonProperty("base_attestation")
    private JsonNode baseAttestation;

    @NotNull
    @JsonProperty("candidate_definition")
    private JsonNode candidateDefinition;

    @NotNull
    private JsonNode validation;
}
