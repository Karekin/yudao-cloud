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
        private String definitionSha256;
        private String definitionClosureSha256;
        private String terminalResultSha256;
        private String riskLevel;
        private String approvalRef;
        private String approvalScopeSkillId;
        private String approvalScopeSkillVersion;
        private String approvalScopeDefinitionClosureSha256;
        private String approvalScopeInputSha256;
        private String approvalScopeRiskLevel;
        private String parentTaskId;
        private String parentStepCode;
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
        private String stepKind;
        private String capabilityId;
        private String operationType;
        private String childSkillId;
        private String childSkillVersion;
        private String childRunIdTemplate;
        private String childTaskId;
        private Integer pollIntervalSeconds;
        private String waitSuccessJson;
        private String waitFailureJson;
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

    @Data
    public static class PermitConsumption {
        private Long tenantId;
        private String permitId;
        private String approvalId;
        private String workOrderId;
        private String rootRequestIdentity;
        private String clientRequestKey;
        private String taskId;
        private String approvalRefSha256;
        private String definitionClosureSha256;
        private String inputSha256;
        private String riskLevel;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
