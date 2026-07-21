package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 岗位角色授予记录视图（管理员治理只读）。
 * 对齐 cloudmold_agent_actor_role_grant 表，供角色授予管理页列表 + 撤销回填 grantId/expectedVersion。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorRoleGrantView implements Serializable {
    private static final long serialVersionUID = 1L;
    private String grantId;
    private Long actorUserId;
    private String roleCode;
    private String status;
    private Instant validFrom;
    private Instant validUntil;
    private Long grantedByUserId;
    private Long version;
    private Instant grantedAt;
    private Instant updatedAt;
}
