package cn.iocoder.yudao.module.cloudmold.skilltask.definition;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("schema_version")
    private String schemaVersion;
    @JsonProperty("skill_id")
    private String skillId;
    @JsonProperty("skill_version")
    private String skillVersion;
    @JsonProperty("risk_level")
    private String riskLevel;
    @JsonProperty("max_attempts")
    private Integer maxAttempts;
    private List<Step> steps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
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
