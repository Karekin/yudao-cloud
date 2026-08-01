package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowRegistryEvaluationRequest {

    @NotBlank
    @JsonProperty("workflow_id")
    private String workflowId;

    @NotBlank
    @JsonProperty("candidate_version_id")
    private String candidateVersionId;

    @NotNull
    @Min(0)
    @JsonProperty("expected_pointer_version")
    private Long expectedPointerVersion;

    @NotBlank
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    @NotBlank
    @JsonProperty("validation_request_id")
    private String validationRequestId;

    @NotBlank
    @JsonProperty("evaluator_run_id")
    private String evaluatorRunId;

    @NotBlank
    @JsonProperty("dataset_sha256")
    private String datasetSha256;

    @Min(1)
    @JsonProperty("sample_count")
    private int sampleCount;

    @NotNull
    @JsonProperty("observation_started_at")
    private LocalDateTime observationStartedAt;

    @NotNull
    @JsonProperty("observation_ended_at")
    private LocalDateTime observationEndedAt;

    @JsonProperty("replay_passed")
    private boolean replayPassed;

    @JsonProperty("shadow_passed")
    private boolean shadowPassed;

    @JsonProperty("canary_passed")
    private boolean canaryPassed;

    @JsonProperty("guardrails_passed")
    private boolean guardrailsPassed;

    @JsonProperty("sample_passed")
    private boolean samplePassed;

    @JsonProperty("dqc_passed")
    private boolean dqcPassed;

    @JsonProperty("observation_window_passed")
    private boolean observationWindowPassed;

    @NotNull
    @JsonProperty("evidence_refs")
    private JsonNode evidenceRefs;

    @NotNull
    private JsonNode metrics;

    @NotNull
    @JsonProperty("failure_samples")
    private JsonNode failureSamples;

    @NotNull
    @JsonProperty("validator_attestation")
    private JsonNode validatorAttestation;
}
