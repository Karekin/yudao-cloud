package cn.iocoder.yudao.module.cloudmold.skilltask.api;

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
public class SkillTaskTerminalProofView implements Serializable {
    private Long tenantId;
    private String taskId;
    private String runId;
    private String skillId;
    private String skillVersion;
    private String definitionSha256;
    private String definitionClosureSha256;
    private String inputSha256;
    private String riskLevel;
    private String status;
    private String terminalResultSha256;
    private Long taskVersion;
    private Instant completedAt;
}
