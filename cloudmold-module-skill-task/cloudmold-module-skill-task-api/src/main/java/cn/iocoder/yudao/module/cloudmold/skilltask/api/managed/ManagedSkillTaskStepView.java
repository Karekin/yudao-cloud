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
public class ManagedSkillTaskStepView implements Serializable {

    private String taskId;
    private String stepCode;
    private String displayName;
    private String resultSummary;
    private Integer stepOrder;
    private String stepKind;
    private String capabilityId;
    private String operationType;
    private String childSkillId;
    private String childSkillVersion;
    private String childTaskId;
    private Integer pollIntervalSeconds;
    private String idempotencyKey;
    private String status;
    private Integer attemptCount;
    private String requestSha256;
    private String resultSha256;
    private String lastErrorCode;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private ManagedSkillTaskChildRunView childTask;
}
