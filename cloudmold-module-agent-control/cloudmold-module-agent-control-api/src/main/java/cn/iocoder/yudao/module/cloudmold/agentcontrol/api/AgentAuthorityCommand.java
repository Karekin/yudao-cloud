package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAuthorityCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private AgentAuthorityOperation operation;
    private String idempotencyKey;
    private Instant occurredAt;
    private RoleGrantDefinition roleGrant;
    private ApprovalGrantDefinition approvalGrant;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RoleGrantDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String grantId;
        private Long actorUserId;
        private String roleCode;
        private Instant validFrom;
        private Instant validUntil;
        private Long expectedVersion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApprovalGrantDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        private String grantId;
        private Long approverUserId;
        private String approvalId;
        private String roleCode;
        private String actionCode;
        private String riskLevel;
        private String scopeHash;
        private Instant validFrom;
        private Instant validUntil;
        private Long expectedVersion;
    }
}
