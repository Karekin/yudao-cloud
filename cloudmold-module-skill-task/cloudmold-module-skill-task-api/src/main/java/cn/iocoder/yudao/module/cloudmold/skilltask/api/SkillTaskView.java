package cn.iocoder.yudao.module.cloudmold.skilltask.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTaskView implements java.io.Serializable {

    private String taskId;
    private String runId;
    private String skillId;
    private String skillVersion;
    private String clientRequestKey;
    private String inputSha256;
    private String definitionSha256;
    private String definitionClosureSha256;
    private String terminalResultSha256;
    private String riskLevel;
    private String approvalRef;
    private Long submitterId;
    private Integer submitterType;
    private Long operatorId;
    private Integer operatorType;
    private String status;
    private String currentStepCode;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Long version;
    private Instant nextRetryAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
