package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTaskDefinition {

    @JsonIgnore
    private String definitionSha256;
    @JsonIgnore
    private String definitionClosureSha256;

    @JsonProperty("schema_version")
    private String schemaVersion;
    @JsonProperty("skill_id")
    private String skillId;
    @JsonProperty("skill_version")
    private String skillVersion;
    @JsonProperty("risk_level")
    private String riskLevel;
    /**
     * BUSINESS_ROLE definitions are user-facing operating workflows.
     * INTERNAL_SUBFLOW definitions remain reusable orchestration building blocks,
     * but are not seeded or displayed as independent AI operators.
     */
    @Builder.Default
    @JsonProperty("workflow_level")
    private String workflowLevel = "BUSINESS_ROLE";
    @JsonProperty("owner_role")
    private String ownerRole;
    @JsonProperty("max_attempts")
    private Integer maxAttempts;
    private List<Step> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        @JsonProperty("step_kind")
        private String stepKind;
        @JsonProperty("step_code")
        private String stepCode;
        @JsonProperty("step_order")
        private Integer stepOrder;
        @JsonProperty("capability_id")
        private String capabilityId;
        @JsonProperty("operation_type")
        private String operationType;
        @JsonProperty("approval_required")
        private Boolean approvalRequired;
        @JsonProperty("idempotency_binding")
        private IdempotencyBinding idempotencyBinding;
        @JsonProperty("child_skill_id")
        private String childSkillId;
        @JsonProperty("child_skill_version")
        private String childSkillVersion;
        @JsonProperty("child_run_id")
        private String childRunId;
        @JsonProperty("poll_interval_seconds")
        private Integer pollIntervalSeconds;
        @JsonProperty("wait_success")
        private JsonNode waitSuccess;
        @JsonProperty("wait_failure")
        private JsonNode waitFailure;
        private JsonNode arguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdempotencyBinding {
        @JsonProperty("argument_index")
        private Integer argumentIndex;
        @JsonProperty("json_pointer")
        private String jsonPointer;
    }
}
