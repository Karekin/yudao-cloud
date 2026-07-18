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
public class SkillTaskStepView implements java.io.Serializable {

    private String taskId;
    private String stepCode;
    private Integer stepOrder;
    private String capabilityId;
    private String operationType;
    private String status;
    private String idempotencyKey;
    private Integer attemptCount;
    private String requestSha256;
    private String resultSha256;
    private String resultJson;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
