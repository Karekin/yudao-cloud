package cn.iocoder.yudao.module.cloudmold.skilltask.dal;

import lombok.Data;

import java.time.LocalDateTime;

public final class SkillTaskRecords {

    private SkillTaskRecords() {
    }

    @Data
    public static class Task {
        private Long tenantId;
        private String taskId;
        private String runId;
        private String skillId;
        private String skillVersion;
        private String clientRequestKey;
        private String inputJson;
        private String inputSha256;
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
        private LocalDateTime nextRetryAt;
        private String leaseOwner;
        private LocalDateTime leaseUntil;
        private String lastErrorCode;
        private String lastErrorMessage;
        private Long version;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class Step {
        private Long tenantId;
        private String taskId;
        private String stepCode;
        private Integer stepOrder;
        private String capabilityId;
        private String operationType;
        private String argumentTemplateJson;
        private String requestJson;
        private String requestSha256;
        private String resultJson;
        private String resultSha256;
        private String idempotencyKey;
        private String status;
        private Integer attemptCount;
        private String lastErrorCode;
        private String lastErrorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class Candidate {
        private Long tenantId;
        private String taskId;
        private String status;
        private Long version;
        private Integer attemptCount;
        private Integer maxAttempts;
    }
}
