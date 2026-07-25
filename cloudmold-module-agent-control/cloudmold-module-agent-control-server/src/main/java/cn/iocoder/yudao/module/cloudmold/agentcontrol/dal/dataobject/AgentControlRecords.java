package cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

public final class AgentControlRecords {
    private AgentControlRecords() {}

    @Data @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class RoleDefinition {
        private String roleId;
        private Long tenantId;
        private String roleCode;
        private String roleName;
        private String responsibilityJson;
        private String kpiJson;
        private String approvalBoundaryJson;
        private String memoryPolicyJson;
        private String status;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class RoleActionPolicy {
        private String policyId;
        private Long tenantId;
        private String roleCode;
        private String actionCode;
        private String permissionMode;
        private String riskLevel;
        private Boolean approvalRequired;
        private Boolean executionRequired;
        private String skillId;
        private String skillVersion;
        private String skillDefinitionClosureSha256;
        private Boolean enabled;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ActorRoleGrant {
        private String grantId;
        private Long tenantId;
        private Long actorUserId;
        private String roleCode;
        private String status;
        private LocalDateTime validFrom;
        private LocalDateTime validUntil;
        private Long grantedByUserId;
        private Long revokedByUserId;
        private String revokeReason;
        private Long version;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ApprovalAuthorityGrant {
        private String grantId;
        private Long tenantId;
        private Long approverUserId;
        private Long requesterUserId;
        private String approvalId;
        private String roleCode;
        private String actionCode;
        private String riskLevel;
        private String scopeHash;
        private String status;
        private LocalDateTime validFrom;
        private LocalDateTime validUntil;
        private Long grantedByUserId;
        private Long revokedByUserId;
        private String revokeReason;
        private Long version;
        private LocalDateTime grantedAt;
        private LocalDateTime revokedAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class WorkOrder {
        private String workOrderId;
        private Long tenantId;
        private String roleCode;
        private String actionCode;
        private String title;
        private String businessContextJson;
        private String status;
        private Long requesterUserId;
        private Long assigneeUserId;
        private String approvalId;
        private String actionPolicyId;
        private Long actionPolicyVersion;
        private String riskLevel;
        private Boolean executionRequired;
        private String skillId;
        private String skillVersion;
        private String skillDefinitionClosureSha256;
        private String executionInputSha256;
        private String missionId;
        private String goalId;
        private String parentWorkOrderId;
        private LocalDateTime deadlineAt;
        private LocalDateTime readyAt;
        private String waitingReasonCode;
        private String activeRunId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime completedAt;
    }

    @Data @Accessors(chain = true)
    public static class Handoff {
        private String handoffId;
        private Long tenantId;
        private String workOrderId;
        private String targetWorkOrderId;
        private String fromRoleCode;
        private String fromActionCode;
        private String toRoleCode;
        private String toActionCode;
        private String summary;
        private String status;
        private Long requestedByUserId;
        private Long acceptedByUserId;
        private Long version;
        private LocalDateTime requestedAt;
        private LocalDateTime acceptedAt;
    }

    @Data @Accessors(chain = true)
    public static class Mission {
        private String missionId; private Long tenantId; private String missionType; private String templateVersion;
        private String title; private String objectiveJson; private String correlationId; private String status;
        private Long supervisorUserId; private Long version; private LocalDateTime startedAt;
        private LocalDateTime deadlineAt; private LocalDateTime completedAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class MissionGoal {
        private String goalId; private Long tenantId; private String missionId; private String goalCode;
        private String title; private String status; private Long version; private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class WorkDependency {
        private String dependencyId; private Long tenantId; private String missionId;
        private String predecessorWorkOrderId; private String successorWorkOrderId; private String status;
        private String satisfiedByResultId; private Long version; private LocalDateTime satisfiedAt;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class EventSubscription {
        private String subscriptionId; private Long tenantId; private String missionId; private String workOrderId;
        private String eventType; private String schemaVersion; private String sourceSystem; private String aggregateType;
        private String aggregateId; private String correlationId; private String matcherCode; private String status;
        private String matchedEventId; private String matchedPayloadSha256; private LocalDateTime matchedAt;
        private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class MissionTimer {
        private String timerId; private Long tenantId; private String missionId; private String workOrderId;
        private String timerType; private LocalDateTime dueAt; private Integer generation; private String status;
        private LocalDateTime firedAt; private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AgentRunLease {
        private Long tenantId; private String workOrderId; private String missionId; private String runId;
        private String triggerType; private String triggerId; private Long actorUserId; private String roleCode;
        private String leaseOwner; private String leaseToken; private Long fencingToken; private LocalDateTime leaseUntil;
        private String status; private Long version; private LocalDateTime startedAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AgentRun {
        private String runId; private Long tenantId; private String missionId; private String workOrderId;
        private String triggerType; private String triggerId; private Long actorUserId; private String roleCode;
        private Long fencingToken; private String status; private LocalDateTime startedAt; private LocalDateTime completedAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class MissionCheckpoint {
        private String checkpointId; private Long tenantId; private String missionId; private String workOrderId;
        private String runId; private Long fencingToken; private String decisionCode; private String decisionJson;
        private String decisionSha256; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Approval {
        private String approvalId;
        private Long tenantId;
        private String workOrderId;
        private String actionCode;
        private Long requesterUserId;
        private Long approverUserId;
        private String scopeHash;
        private String status;
        private String reasonCode;
        private Long version;
        private LocalDateTime requestedAt;
        private LocalDateTime decidedAt;
    }

    @Data @Accessors(chain = true)
    public static class ApprovalWorkflowBinding {
        private String approvalId;
        private Long tenantId;
        private String workOrderId;
        private String actionCode;
        private String roleCode;
        private String riskLevel;
        private Long requesterUserId;
        private Long approverUserId;
        private String scopeHash;
        private String processDefinitionKey;
        private String processInstanceId;
        private String businessKey;
        private String status;
        private Integer lastBpmStatus;
        private String lastReasonSha256;
        private Long terminalOperatorUserId;
        private String terminalTaskId;
        private String terminalTaskDefinitionKey;
        private String startAttemptToken;
        private Integer startAttemptCount;
        private Long version;
        private LocalDateTime requestedAt;
        private LocalDateTime startAttemptedAt;
        private LocalDateTime startedAt;
        private LocalDateTime terminalAt;
        private String lastErrorCode;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ApprovalWorkflowStartCandidate {
        private String approvalId;
        private Long tenantId;
        private String workOrderId;
        private String actionCode;
        private String roleCode;
        private String riskLevel;
        private Long requesterUserId;
        private Long approverUserId;
        private String scopeHash;
        private String processDefinitionKey;
        private String businessKey;
        private String status;
        private Long version;
    }

    @Data @Accessors(chain = true)
    public static class ApprovalWorkflowEvent {
        private String eventId;
        private Long tenantId;
        private String approvalId;
        private String processInstanceId;
        private Integer bpmStatus;
        private String observedStatus;
        private String reasonSha256;
        private Long terminalOperatorUserId;
        private String terminalTaskId;
        private String terminalTaskDefinitionKey;
        private LocalDateTime observedAt;
    }

    @Data @Accessors(chain = true)
    public static class BusinessResult {
        private String resultId;
        private Long tenantId;
        private String workOrderId;
        private String outcomeCode;
        private String summary;
        private String evidenceRef;
        private Long recordedByUserId;
        private LocalDateTime recordedAt;
    }

    @Data @Accessors(chain = true)
    public static class ExecutionBinding {
        private String bindingId;
        private Long tenantId;
        private String workOrderId;
        private Integer executionGeneration;
        private String skillTaskId;
        private String skillId;
        private String skillVersion;
        private String skillDefinitionClosureSha256;
        private String inputSha256;
        private String terminalResultSha256;
        private String status;
        private Long version;
        private LocalDateTime boundAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class ExecutionReconcileCandidate {
        private String bindingId;
        private Long tenantId;
        private Long operatorUserId;
        private String skillTaskId;
        private String skillId;
        private String runId;
    }

    @Data @Accessors(chain = true)
    public static class MissionResolutionCandidate {
        private Long tenantId;
        private String workOrderId;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AuditEvent {
        private String auditEventId;
        private Long tenantId;
        private String aggregateType;
        private String aggregateId;
        private Long aggregateVersion;
        private String eventType;
        private Long actorUserId;
        private String detailJson;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class LegacyPurchaseInFact {
        private Long purchaseInId;
        private Long tenantId;
        private String purchaseInNo;
        private Integer status;
        private Long supplierId;
        private Long orderId;
        private java.math.BigDecimal totalCount;
        private LocalDateTime sourceUpdatedAt;
    }

    @Data @Accessors(chain = true)
    public static class LegacyPurchaseInBridgeState {
        private Long tenantId;
        private Long purchaseInId;
        private Integer observedStatus;
        private LocalDateTime sourceUpdatedAt;
        private String eventId;
        private LocalDateTime capturedAt;
    }
}
