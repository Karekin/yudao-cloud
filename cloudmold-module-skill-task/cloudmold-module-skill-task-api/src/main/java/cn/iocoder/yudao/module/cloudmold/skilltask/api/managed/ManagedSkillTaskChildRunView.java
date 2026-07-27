package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskChildRunView implements Serializable {

    private String taskId;
    private String runId;
    private String skillId;
    private String skillVersion;
    private String inputSha256;
    private String definitionSha256;
    private String definitionClosureSha256;
    private String terminalResultSha256;
    private String riskLevel;
    private String status;
    private String currentStepCode;
    private Long version;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
