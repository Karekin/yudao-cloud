package cn.iocoder.yudao.module.cloudmold.aioperations.service.command;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class ManagedSkillTaskTriggerResult {

    String taskId;
    String runId;
    String skillId;
    String skillVersion;
    String riskLevel;
    String status;
    String currentStepCode;
    String inputSha256;
    String definitionClosureSha256;
    String approvalRefSha256;
    Instant approvalExpiresAt;
    Instant createdAt;
}
