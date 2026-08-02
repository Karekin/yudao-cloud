package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Skill 定义中的一个已登记编排步骤。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskWorkflowStepView implements Serializable {

    private Integer stepOrder;
    private String stepCode;
    private String displayName;
    private String stepKind;
    private String operationType;
    private Boolean approvalRequired;
    private String capabilityId;
    private String childSkillId;
    private String childSkillVersion;
    private Integer pollIntervalSeconds;
    private ManagedSkillTaskWorkflowIdempotencyBindingView idempotencyBinding;
    private String waitSuccessJson;
    private String waitFailureJson;
    private String argumentsJson;
}
