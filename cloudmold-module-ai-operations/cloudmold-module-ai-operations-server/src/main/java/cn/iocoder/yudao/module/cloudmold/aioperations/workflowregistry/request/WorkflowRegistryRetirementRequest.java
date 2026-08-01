package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowRegistryRetirementRequest {

    @NotBlank
    @JsonProperty("workflow_id")
    private String workflowId;

    @NotBlank
    @JsonProperty("target_version_id")
    private String targetVersionId;

    @NotBlank
    private String action;

    @NotNull
    @Min(0)
    @JsonProperty("expected_pointer_version")
    private Long expectedPointerVersion;

    @NotBlank
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    private String reason;

    @NotNull
    @JsonProperty("evidence_refs")
    private JsonNode evidenceRefs;

    @NotNull
    private JsonNode metrics;
}
