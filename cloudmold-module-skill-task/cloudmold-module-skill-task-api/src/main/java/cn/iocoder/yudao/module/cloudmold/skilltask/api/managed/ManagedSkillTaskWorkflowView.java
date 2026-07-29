package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskWorkflowView implements Serializable {

    private String skillId;
    private String skillVersion;
    private String displayName;
    private String description;
    private String workflowLevel;
    private String ownerRole;
    private String riskLevel;
    private Integer maxAttempts;
    private Integer stepCount;
    private Integer writeStepCount;
    private Boolean approvalRequired;
    private String definitionSha256;
    private String definitionClosureSha256;
    private String triggerSource;
    private String managementSurface;
    /**
     * @deprecated use {@link #triggerSource} and {@link #managementSurface}.
     */
    @Deprecated
    private String orchestrationSurface;
    private String durableAuthority;
}
