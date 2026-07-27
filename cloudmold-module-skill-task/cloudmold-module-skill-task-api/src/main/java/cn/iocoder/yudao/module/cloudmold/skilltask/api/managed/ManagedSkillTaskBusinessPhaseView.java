package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskBusinessPhaseView implements Serializable {

    private String phaseCode;
    private String displayName;
    private String description;
    private Integer phaseOrder;
    private Integer depth;
    private String taskId;
    private String runId;
    private String skillId;
    private String skillVersion;
    private String parentTaskId;
    private String status;
    private String riskLevel;
    private Boolean approvalRequired;
    private String approvalStatus;
    private ManagedSkillTaskBusinessOutcomeView businessOutcome;
    private List<ManagedSkillTaskBusinessActionView> actions;
    private Instant startedAt;
    private Instant completedAt;
}
