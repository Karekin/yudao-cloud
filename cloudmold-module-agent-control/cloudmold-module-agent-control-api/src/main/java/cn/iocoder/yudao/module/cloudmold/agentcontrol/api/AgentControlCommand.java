package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentControlCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private AgentControlOperation operation;
    private String idempotencyKey;
    private Instant occurredAt;
    private RoleDefinition role;
    private RoleActionPolicyDefinition actionPolicy;
    private WorkOrderDefinition workOrder;
    private HandoffDefinition handoff;
    private ApprovalDefinition approval;
    private BusinessResultDefinition businessResult;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RoleDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String roleId;
        private String roleCode;
        private String roleName;
        private String responsibilityJson;
        private String kpiJson;
        private String approvalBoundaryJson;
        private String memoryPolicyJson;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RoleActionPolicyDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String policyId;
        private String roleCode;
        private String actionCode;
        private String riskLevel;
        private Boolean approvalRequired;
        private Boolean executionRequired;
        private String skillId;
        private String skillVersion;
        private String skillDefinitionClosureSha256;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WorkOrderDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workOrderId;
        private String roleCode;
        private String actionCode;
        private String title;
        private String businessContextJson;
        private Long workOrderExpectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HandoffDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String handoffId;
        private String workOrderId;
        private String toRoleCode;
        private String toActionCode;
        private String summary;
        private Long handoffExpectedVersion;
        private Long workOrderExpectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApprovalDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String approvalId;
        private String workOrderId;
        private String decision;
        private String reasonCode;
        private Long approvalExpectedVersion;
        private Long workOrderExpectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BusinessResultDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String resultId;
        private String workOrderId;
        private String outcomeCode;
        private String summary;
        private String evidenceRef;
        private Long workOrderExpectedVersion;
    }
}
