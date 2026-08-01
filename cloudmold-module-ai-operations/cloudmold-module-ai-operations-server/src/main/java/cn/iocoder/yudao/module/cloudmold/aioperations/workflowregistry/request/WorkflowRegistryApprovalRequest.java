package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowRegistryApprovalRequest {

    @NotBlank
    @JsonProperty("workflow_id")
    private String workflowId;

    @NotBlank
    @JsonProperty("candidate_version_id")
    private String candidateVersionId;

    @NotBlank
    @JsonProperty("evaluation_id")
    private String evaluationId;

    @NotNull
    @Min(0)
    @JsonProperty("expected_pointer_version")
    private Long expectedPointerVersion;

    @NotBlank
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    @NotBlank
    private String decision;

    private String rationale;

    @NotNull
    @JsonProperty("evidence_refs")
    private JsonNode evidenceRefs;

    @NotNull
    private JsonNode metrics;
}
