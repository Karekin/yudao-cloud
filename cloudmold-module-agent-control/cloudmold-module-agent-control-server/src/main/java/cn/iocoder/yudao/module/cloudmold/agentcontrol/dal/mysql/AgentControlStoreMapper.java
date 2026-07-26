package cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.ActorRoleGrantView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentBusinessCardView;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentControlStoreMapper {

    @Select("""
            SELECT id AS purchase_in_id,tenant_id,no AS purchase_in_no,status,supplier_id,order_id,total_count,
                   update_time AS source_updated_at
            FROM erp_purchase_in
            WHERE tenant_id=#{tenantId} AND id=#{purchaseInId} AND deleted=b'0'
            """)
    LegacyPurchaseInFact selectLegacyPurchaseInFact(@Param("tenantId") Long tenantId,
                                                     @Param("purchaseInId") Long purchaseInId);

    @Select("""
            SELECT tenant_id,purchase_in_id,observed_status,source_updated_at,event_id,captured_at
            FROM cloudmold_agent_legacy_purchase_in_bridge
            WHERE tenant_id=#{tenantId} AND purchase_in_id=#{purchaseInId} FOR UPDATE
            """)
    LegacyPurchaseInBridgeState selectLegacyPurchaseInBridgeForUpdate(@Param("tenantId") Long tenantId,
                                                                       @Param("purchaseInId") Long purchaseInId);

    @Insert("""
            INSERT INTO cloudmold_agent_legacy_purchase_in_bridge
              (tenant_id,purchase_in_id,observed_status,source_updated_at,event_id,captured_at)
            VALUES (#{tenantId},#{purchaseInId},#{observedStatus},#{sourceUpdatedAt},#{eventId},#{capturedAt})
            ON DUPLICATE KEY UPDATE observed_status=VALUES(observed_status),
              source_updated_at=VALUES(source_updated_at),event_id=VALUES(event_id),captured_at=VALUES(captured_at)
            """)
    int upsertLegacyPurchaseInBridge(LegacyPurchaseInBridgeState value);

    @TenantIgnore
    @Select("""
            SELECT * FROM cloudmold_agent_event_subscription
            WHERE status='ACTIVE' AND event_type='legacy.erp.purchase_in.status_changed'
              AND schema_version='v1' AND source_system='legacy-erp' AND aggregate_type='purchase_in'
            ORDER BY updated_at,subscription_id LIMIT #{limit}
            """)
    List<EventSubscription> selectActiveLegacyPurchaseInSubscriptions(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT card_type,card_id,mission_id,work_order_id,title,role_code,from_role_code,action_code,
                   status,risk_level,outcome_code,summary,occurred_at
            FROM (
              SELECT 'APPROVAL' AS card_type,a.approval_id AS card_id,w.mission_id,w.work_order_id,w.title,
                     w.role_code,NULL AS from_role_code,w.action_code,a.status,w.risk_level,
                     NULL AS outcome_code,a.reason_code AS summary,a.requested_at AS occurred_at
              FROM cloudmold_agent_approval a
              JOIN cloudmold_agent_work_order w
                ON w.tenant_id=a.tenant_id AND w.work_order_id=a.work_order_id
              WHERE a.tenant_id=#{tenantId}
              UNION ALL
              SELECT 'HANDOFF',h.handoff_id,w.mission_id,
                     COALESCE(h.target_work_order_id,h.work_order_id),w.title,h.to_role_code,
                     h.from_role_code,h.to_action_code,h.status,w.risk_level,NULL,h.summary,h.requested_at
              FROM cloudmold_agent_role_handoff h
              JOIN cloudmold_agent_work_order w
                ON w.tenant_id=h.tenant_id
               AND w.work_order_id=COALESCE(h.target_work_order_id,h.work_order_id)
              WHERE h.tenant_id=#{tenantId}
              UNION ALL
              SELECT 'RESULT',r.result_id,w.mission_id,w.work_order_id,w.title,w.role_code,NULL,w.action_code,
                     w.status,w.risk_level,r.outcome_code,r.summary,r.recorded_at
              FROM cloudmold_agent_business_result r
              JOIN cloudmold_agent_work_order w
                ON w.tenant_id=r.tenant_id AND w.work_order_id=r.work_order_id
              WHERE r.tenant_id=#{tenantId}
            ) cards
            WHERE 1=1
            <if test="roleCode != null">AND role_code=#{roleCode}</if>
            <if test="cardType != null">AND card_type=#{cardType}</if>
            <if test="status != null">AND status=#{status}</if>
            ORDER BY occurred_at DESC,card_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AgentBusinessCardView> selectBusinessCards(@Param("tenantId") Long tenantId,
                                                    @Param("roleCode") String roleCode,
                                                    @Param("cardType") String cardType,
                                                    @Param("status") String status,
                                                    @Param("limit") int limit);

    @Select("""
            <script>
            SELECT grant_id,actor_user_id,role_code,status,valid_from,valid_until,granted_by_user_id,
                   version,granted_at,updated_at
            FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId}
            <if test="roleCode != null">AND role_code=#{roleCode}</if>
            <if test="status != null">AND status=#{status}</if>
            <if test="actorUserId != null">AND actor_user_id=#{actorUserId}</if>
            ORDER BY granted_at DESC,grant_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<ActorRoleGrantView> selectActorRoleGrants(@Param("tenantId") Long tenantId,
                                                    @Param("roleCode") String roleCode,
                                                    @Param("status") String status,
                                                    @Param("actorUserId") Long actorUserId,
                                                    @Param("limit") int limit);

    @Insert("""
            INSERT INTO cloudmold_agent_control_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_agent_control_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_agent_control_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_role_definition
              (role_id,tenant_id,role_code,role_name,responsibility_json,kpi_json,approval_boundary_json,
               memory_policy_json,status,version,created_at,updated_at)
            VALUES (#{roleId},#{tenantId},#{roleCode},#{roleName},CAST(#{responsibilityJson} AS JSON),
                    CAST(#{kpiJson} AS JSON),CAST(#{approvalBoundaryJson} AS JSON),CAST(#{memoryPolicyJson} AS JSON),
                    #{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertRole(RoleDefinition value);

    @Select("""
            SELECT role_id,tenant_id,role_code,role_name,responsibility_json,kpi_json,approval_boundary_json,
                   memory_policy_json,status,version,created_at,updated_at
            FROM cloudmold_agent_role_definition WHERE tenant_id=#{tenantId} AND role_code=#{roleCode}
            """)
    RoleDefinition selectRole(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode);

    @Insert("""
            INSERT INTO cloudmold_agent_role_action_policy
              (policy_id,tenant_id,role_code,action_code,permission_mode,risk_level,approval_required,enabled,
               execution_required,skill_id,skill_version,skill_definition_closure_sha256,version,created_at,updated_at)
            VALUES (#{policyId},#{tenantId},#{roleCode},#{actionCode},#{permissionMode},#{riskLevel},
                    #{approvalRequired},#{enabled},#{executionRequired},#{skillId},#{skillVersion},
                    #{skillDefinitionClosureSha256},#{version},#{createdAt},#{updatedAt})
            """)
    int insertActionPolicy(RoleActionPolicy value);

    @Select("""
            SELECT policy_id,tenant_id,role_code,action_code,permission_mode,risk_level,approval_required,
                   enabled,execution_required,skill_id,skill_version,skill_definition_closure_sha256,
                   version,created_at,updated_at
            FROM cloudmold_agent_role_action_policy
            WHERE tenant_id=#{tenantId} AND role_code=#{roleCode} AND action_code=#{actionCode}
            """)
    RoleActionPolicy selectActionPolicy(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode,
                                        @Param("actionCode") String actionCode);

    @Insert("""
            INSERT INTO cloudmold_agent_actor_role_grant
              (grant_id,tenant_id,actor_user_id,role_code,status,valid_from,valid_until,granted_by_user_id,
               revoked_by_user_id,revoke_reason,version,granted_at,revoked_at,updated_at)
            VALUES (#{grantId},#{tenantId},#{actorUserId},#{roleCode},#{status},#{validFrom},#{validUntil},
                    #{grantedByUserId},#{revokedByUserId},#{revokeReason},#{version},#{grantedAt},#{revokedAt},
                    #{updatedAt})
            """)
    int insertActorRoleGrant(ActorRoleGrant value);

    @Select("""
            SELECT grant_id,tenant_id,actor_user_id,role_code,status,valid_from,valid_until,granted_by_user_id,
                   revoked_by_user_id,revoke_reason,version,granted_at,revoked_at,updated_at
            FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId} AND grant_id=#{grantId} FOR UPDATE
            """)
    ActorRoleGrant selectActorRoleGrantForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("grantId") String grantId);

    @Select("""
            SELECT grant_id,tenant_id,actor_user_id,role_code,status,valid_from,valid_until,granted_by_user_id,
                   revoked_by_user_id,revoke_reason,version,granted_at,revoked_at,updated_at
            FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND role_code=#{roleCode}
              AND status='ACTIVE' FOR UPDATE
            """)
    ActorRoleGrant selectActiveActorRoleGrantForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("actorUserId") Long actorUserId,
                                                        @Param("roleCode") String roleCode);

    @Select("""
            SELECT grant_id,tenant_id,actor_user_id,role_code,status,valid_from,valid_until,granted_by_user_id,
                   revoked_by_user_id,revoke_reason,version,granted_at,revoked_at,updated_at
            FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND role_code=#{roleCode}
              AND status='ACTIVE' AND valid_from<=#{now} AND valid_until>#{now}
            LIMIT 1 FOR SHARE
            """)
    ActorRoleGrant selectEffectiveActorRoleGrant(@Param("tenantId") Long tenantId,
                                                  @Param("actorUserId") Long actorUserId,
                                                  @Param("roleCode") String roleCode,
                                                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_actor_role_grant
            SET status='EXPIRED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND role_code=#{roleCode}
              AND status='ACTIVE' AND valid_until<=#{now}
            """)
    int expireActorRoleGrant(@Param("tenantId") Long tenantId, @Param("actorUserId") Long actorUserId,
                             @Param("roleCode") String roleCode, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_actor_role_grant
            SET status='REVOKED',revoked_by_user_id=#{revokedByUserId},revoke_reason=#{revokeReason},
                revoked_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND grant_id=#{grantId} AND version=#{expectedVersion}
              AND status='ACTIVE' AND actor_user_id<>#{revokedByUserId}
            """)
    int revokeActorRoleGrant(@Param("tenantId") Long tenantId, @Param("grantId") String grantId,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("revokedByUserId") Long revokedByUserId,
                             @Param("revokeReason") String revokeReason, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_approval_authority_grant
              (grant_id,tenant_id,approver_user_id,requester_user_id,approval_id,role_code,action_code,risk_level,scope_hash,status,
               valid_from,valid_until,granted_by_user_id,revoked_by_user_id,revoke_reason,version,granted_at,
               revoked_at,updated_at)
            VALUES (#{grantId},#{tenantId},#{approverUserId},#{requesterUserId},#{approvalId},#{roleCode},#{actionCode},#{riskLevel},
                    #{scopeHash},#{status},#{validFrom},#{validUntil},#{grantedByUserId},#{revokedByUserId},
                    #{revokeReason},#{version},#{grantedAt},#{revokedAt},#{updatedAt})
            """)
    int insertApprovalAuthorityGrant(ApprovalAuthorityGrant value);

    @Select("""
            SELECT grant_id,tenant_id,approver_user_id,requester_user_id,approval_id,role_code,action_code,risk_level,scope_hash,
                   status,valid_from,valid_until,granted_by_user_id,revoked_by_user_id,revoke_reason,version,
                   granted_at,revoked_at,updated_at
            FROM cloudmold_agent_approval_authority_grant
            WHERE tenant_id=#{tenantId} AND grant_id=#{grantId} FOR UPDATE
            """)
    ApprovalAuthorityGrant selectApprovalAuthorityGrantForUpdate(@Param("tenantId") Long tenantId,
                                                                  @Param("grantId") String grantId);

    @Select("""
            SELECT grant_id,tenant_id,approver_user_id,requester_user_id,approval_id,role_code,action_code,risk_level,scope_hash,
                   status,valid_from,valid_until,granted_by_user_id,revoked_by_user_id,revoke_reason,version,
                   granted_at,revoked_at,updated_at
            FROM cloudmold_agent_approval_authority_grant
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} AND status='ACTIVE' FOR UPDATE
            """)
    ApprovalAuthorityGrant selectActiveApprovalAuthorityGrantForUpdate(@Param("tenantId") Long tenantId,
                                                                        @Param("approvalId") String approvalId);

    @Select("""
            SELECT grant_id,tenant_id,approver_user_id,requester_user_id,approval_id,role_code,action_code,risk_level,scope_hash,
                   status,valid_from,valid_until,granted_by_user_id,revoked_by_user_id,revoke_reason,version,
                   granted_at,revoked_at,updated_at
            FROM cloudmold_agent_approval_authority_grant
            WHERE tenant_id=#{tenantId} AND approver_user_id=#{approverUserId} AND approval_id=#{approvalId}
              AND role_code=#{roleCode} AND action_code=#{actionCode} AND risk_level=#{riskLevel}
              AND scope_hash=#{scopeHash} AND status='ACTIVE' AND valid_from<=#{now} AND valid_until>#{now}
            LIMIT 1 FOR SHARE
            """)
    ApprovalAuthorityGrant selectEffectiveApprovalAuthorityGrant(
            @Param("tenantId") Long tenantId, @Param("approverUserId") Long approverUserId,
            @Param("approvalId") String approvalId, @Param("roleCode") String roleCode,
            @Param("actionCode") String actionCode, @Param("riskLevel") String riskLevel,
            @Param("scopeHash") String scopeHash, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_approval_authority_grant
            SET status='EXPIRED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId}
              AND status='ACTIVE' AND valid_until<=#{now}
            """)
    int expireApprovalAuthorityGrant(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId,
                                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_approval_authority_grant
            SET status='REVOKED',revoked_by_user_id=#{revokedByUserId},revoke_reason=#{revokeReason},
                revoked_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND grant_id=#{grantId} AND version=#{expectedVersion}
              AND status='ACTIVE' AND approver_user_id<>#{revokedByUserId}
            """)
    int revokeApprovalAuthorityGrant(@Param("tenantId") Long tenantId, @Param("grantId") String grantId,
                                     @Param("expectedVersion") Long expectedVersion,
                                     @Param("revokedByUserId") Long revokedByUserId,
                                     @Param("revokeReason") String revokeReason,
                                     @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_work_order
              (work_order_id,tenant_id,role_code,action_code,title,business_context_json,status,requester_user_id,
               assignee_user_id,approval_id,action_policy_id,action_policy_version,risk_level,execution_required,skill_id,
               skill_version,skill_definition_closure_sha256,execution_input_sha256,mission_id,goal_id,
               parent_work_order_id,deadline_at,ready_at,waiting_reason_code,active_run_id,version,created_at,updated_at,
               completed_at)
            VALUES (#{workOrderId},#{tenantId},#{roleCode},#{actionCode},#{title},CAST(#{businessContextJson} AS JSON),
                    #{status},#{requesterUserId},#{assigneeUserId},#{approvalId},#{actionPolicyId},
                    #{actionPolicyVersion},#{riskLevel},#{executionRequired},#{skillId},#{skillVersion},
                    #{skillDefinitionClosureSha256},#{executionInputSha256},#{missionId},#{goalId},
                    #{parentWorkOrderId},#{deadlineAt},#{readyAt},#{waitingReasonCode},#{activeRunId},
                    #{version},#{createdAt},#{updatedAt},
                    #{completedAt})
            """)
    int insertWorkOrder(WorkOrder value);

    @Select("""
            SELECT work_order_id,tenant_id,role_code,action_code,title,business_context_json,status,
                   requester_user_id,assignee_user_id,approval_id,action_policy_id,action_policy_version,risk_level,
                   execution_required,skill_id,skill_version,skill_definition_closure_sha256,
                   execution_input_sha256,mission_id,goal_id,parent_work_order_id,deadline_at,ready_at,
                   waiting_reason_code,active_run_id,version,created_at,updated_at,completed_at
            FROM cloudmold_agent_work_order WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId}
            """)
    WorkOrder selectWorkOrder(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId);

    @Select("""
            SELECT work_order_id,tenant_id,role_code,action_code,title,business_context_json,status,
                   requester_user_id,assignee_user_id,approval_id,action_policy_id,action_policy_version,risk_level,
                   execution_required,skill_id,skill_version,skill_definition_closure_sha256,
                   execution_input_sha256,mission_id,goal_id,parent_work_order_id,deadline_at,ready_at,
                   waiting_reason_code,active_run_id,version,created_at,updated_at,completed_at
            FROM cloudmold_agent_work_order WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} FOR UPDATE
            """)
    WorkOrder selectWorkOrderForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("workOrderId") String workOrderId);

    @Update("""
            UPDATE cloudmold_agent_work_order
            SET status=#{after},assignee_user_id=COALESCE(#{assigneeUserId},assignee_user_id),version=version+1,
                completed_at=CASE WHEN #{after}='COMPLETED' THEN #{now} ELSE completed_at END,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionWorkOrder(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                            @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                            @Param("after") String after, @Param("assigneeUserId") Long assigneeUserId,
                            @Param("unused") String unused, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_work_order
            SET status=#{after},waiting_reason_code=#{waitingReasonCode},ready_at=CASE WHEN #{after}='READY' THEN #{now} ELSE ready_at END,
                active_run_id=#{activeRunId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND version=#{expectedVersion}
              AND status=#{before}
            """)
    int transitionMissionWorkOrder(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                                   @Param("expectedVersion") Long expectedVersion, @Param("before") String before,
                                   @Param("after") String after, @Param("waitingReasonCode") String waitingReasonCode,
                                   @Param("activeRunId") String activeRunId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_work_order SET approval_id=#{approvalId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND version=#{expectedVersion}
              AND status='WAITING_APPROVAL' AND approval_id IS NULL
            """)
    int attachApproval(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                       @Param("expectedVersion") Long expectedVersion, @Param("approvalId") String approvalId,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_work_order
            SET role_code=#{toRoleCode},action_code=#{toActionCode},assignee_user_id=#{assigneeUserId},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND version=#{expectedVersion}
              AND status='IN_PROGRESS' AND role_code=#{fromRoleCode} AND action_code=#{fromActionCode}
            """)
    int acceptWorkOrderHandoff(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("fromRoleCode") String fromRoleCode,
                               @Param("fromActionCode") String fromActionCode,
                               @Param("toRoleCode") String toRoleCode,
                               @Param("toActionCode") String toActionCode,
                               @Param("assigneeUserId") Long assigneeUserId,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_role_handoff
              (handoff_id,tenant_id,work_order_id,target_work_order_id,from_role_code,from_action_code,to_role_code,to_action_code,
               summary,status,requested_by_user_id,accepted_by_user_id,version,requested_at,accepted_at)
            VALUES (#{handoffId},#{tenantId},#{workOrderId},#{targetWorkOrderId},#{fromRoleCode},#{fromActionCode},#{toRoleCode},
                    #{toActionCode},#{summary},#{status},#{requestedByUserId},#{acceptedByUserId},#{version},
                    #{requestedAt},#{acceptedAt})
            """)
    int insertHandoff(Handoff value);

    @Select("""
            SELECT handoff_id,tenant_id,work_order_id,from_role_code,from_action_code,to_role_code,to_action_code,
                   target_work_order_id,summary,status,
                   requested_by_user_id,accepted_by_user_id,version,requested_at,accepted_at
            FROM cloudmold_agent_role_handoff WHERE tenant_id=#{tenantId} AND handoff_id=#{handoffId}
            """)
    Handoff selectHandoff(@Param("tenantId") Long tenantId, @Param("handoffId") String handoffId);

    @Select("""
            SELECT handoff_id,tenant_id,work_order_id,from_role_code,from_action_code,to_role_code,to_action_code,
                   target_work_order_id,summary,status,
                   requested_by_user_id,accepted_by_user_id,version,requested_at,accepted_at
            FROM cloudmold_agent_role_handoff WHERE tenant_id=#{tenantId} AND handoff_id=#{handoffId} FOR UPDATE
            """)
    Handoff selectHandoffForUpdate(@Param("tenantId") Long tenantId, @Param("handoffId") String handoffId);

    @Update("""
            UPDATE cloudmold_agent_role_handoff
            SET status='ACCEPTED',accepted_by_user_id=#{acceptedByUserId},version=version+1,
                accepted_at=#{now}
            WHERE tenant_id=#{tenantId} AND handoff_id=#{handoffId} AND version=#{expectedVersion}
              AND status='PENDING'
            """)
    int acceptHandoff(@Param("tenantId") Long tenantId, @Param("handoffId") String handoffId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("acceptedByUserId") Long acceptedByUserId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_approval
              (approval_id,tenant_id,work_order_id,action_code,requester_user_id,approver_user_id,scope_hash,status,
               reason_code,version,requested_at,decided_at)
            VALUES (#{approvalId},#{tenantId},#{workOrderId},#{actionCode},#{requesterUserId},#{approverUserId},
                    #{scopeHash},#{status},#{reasonCode},#{version},#{requestedAt},#{decidedAt})
            """)
    int insertApproval(Approval value);

    @Select("""
            SELECT approval_id,tenant_id,work_order_id,action_code,requester_user_id,approver_user_id,scope_hash,status,
                   reason_code,version,requested_at,decided_at
            FROM cloudmold_agent_approval WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId}
            """)
    Approval selectApproval(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId);

    @Select("""
            SELECT approval_id,tenant_id,work_order_id,action_code,requester_user_id,approver_user_id,scope_hash,status,
                   reason_code,version,requested_at,decided_at
            FROM cloudmold_agent_approval WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} FOR UPDATE
            """)
    Approval selectApprovalForUpdate(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId);

    @Update("""
            UPDATE cloudmold_agent_approval
            SET status=#{status},approver_user_id=#{approverUserId},reason_code=#{reasonCode},version=version+1,
                decided_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} AND version=#{expectedVersion}
              AND status='PENDING' AND requester_user_id<>#{approverUserId}
            """)
    int decideApproval(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId,
                       @Param("expectedVersion") Long expectedVersion, @Param("status") String status,
                       @Param("approverUserId") Long approverUserId, @Param("reasonCode") String reasonCode,
                       @Param("now") LocalDateTime now);

    @Insert("""
            INSERT IGNORE INTO cloudmold_agent_approval_workflow_binding
              (approval_id,tenant_id,work_order_id,action_code,role_code,risk_level,requester_user_id,approver_user_id,scope_hash,
               process_definition_key,business_key,status,start_attempt_count,version,requested_at,updated_at)
            VALUES (#{approvalId},#{tenantId},#{workOrderId},#{actionCode},#{roleCode},#{riskLevel},
                    #{requesterUserId},#{approverUserId},#{scopeHash},#{processDefinitionKey},#{businessKey},#{status},
                    #{startAttemptCount},#{version},#{requestedAt},#{updatedAt})
            """)
    int insertApprovalWorkflowBinding(ApprovalWorkflowBinding value);

    @Select("""
            SELECT approval_id,tenant_id,work_order_id,action_code,role_code,risk_level,requester_user_id,approver_user_id,scope_hash,
                   process_definition_key,process_instance_id,business_key,status,last_bpm_status,last_reason_sha256,
                   terminal_operator_user_id,terminal_task_id,terminal_task_definition_key,
                   start_attempt_token,start_attempt_count,version,requested_at,start_attempted_at,started_at,
                   terminal_at,last_error_code,updated_at
            FROM cloudmold_agent_approval_workflow_binding
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId}
            """)
    ApprovalWorkflowBinding selectApprovalWorkflowBinding(@Param("tenantId") Long tenantId,
                                                           @Param("approvalId") String approvalId);

    @TenantIgnore
    @Select("""
            SELECT b.approval_id,b.tenant_id,b.work_order_id,b.action_code,b.role_code,b.risk_level,
                   b.requester_user_id,g.approver_user_id,b.scope_hash,b.process_definition_key,
                   b.business_key,b.status,b.version
            FROM cloudmold_agent_approval_workflow_binding b
            JOIN cloudmold_agent_approval_authority_grant g
              ON g.tenant_id=b.tenant_id AND g.approval_id=b.approval_id
             AND g.status='ACTIVE' AND g.valid_from<=CURRENT_TIMESTAMP(6) AND g.valid_until>CURRENT_TIMESTAMP(6)
            WHERE b.status='START_REQUESTED' AND b.requester_user_id<>g.approver_user_id
            ORDER BY requested_at,tenant_id,approval_id,g.approver_user_id
            LIMIT #{limit}
            """)
    List<ApprovalWorkflowStartCandidate> selectApprovalWorkflowStartCandidates(@Param("limit") int limit);

    @TenantIgnore
    @Select("""
            SELECT b.approval_id,b.tenant_id,b.work_order_id,b.status AS observed_status,
                   b.terminal_operator_user_id,a.version AS approval_version,w.version AS work_order_version
            FROM cloudmold_agent_approval_workflow_binding b
            JOIN cloudmold_agent_approval a
              ON a.tenant_id=b.tenant_id AND a.approval_id=b.approval_id
            JOIN cloudmold_agent_work_order w
              ON w.tenant_id=b.tenant_id AND w.work_order_id=b.work_order_id
            WHERE b.status IN ('BPM_APPROVED_PENDING_ATTESTATION','BPM_REJECTED')
              AND a.status='PENDING'
            ORDER BY b.terminal_at,b.tenant_id,b.approval_id
            LIMIT #{limit}
            """)
    List<ApprovalWorkflowDecisionCandidate> selectApprovalWorkflowDecisionCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_agent_approval_workflow_binding
            SET status='STARTING',approver_user_id=#{approverUserId},
                start_attempt_token=#{attemptToken},start_attempt_count=start_attempt_count+1,
                start_attempted_at=#{now},last_error_code=NULL,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} AND version=#{expectedVersion}
              AND status='START_REQUESTED' AND start_attempt_count=0 AND approver_user_id IS NULL
              AND requester_user_id<>#{approverUserId}
              AND EXISTS (
                SELECT 1 FROM cloudmold_agent_approval_authority_grant g
                WHERE g.tenant_id=cloudmold_agent_approval_workflow_binding.tenant_id
                  AND g.approval_id=cloudmold_agent_approval_workflow_binding.approval_id
                  AND g.approver_user_id=#{approverUserId}
                  AND g.role_code=cloudmold_agent_approval_workflow_binding.role_code
                  AND g.action_code=cloudmold_agent_approval_workflow_binding.action_code
                  AND g.risk_level=cloudmold_agent_approval_workflow_binding.risk_level
                  AND g.scope_hash=cloudmold_agent_approval_workflow_binding.scope_hash
                  AND g.status='ACTIVE'
                  AND g.valid_from<=#{now} AND g.valid_until>#{now}
              )
            """)
    int claimApprovalWorkflowStart(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId,
                                   @Param("expectedVersion") Long expectedVersion,
                                   @Param("approverUserId") Long approverUserId,
                                   @Param("attemptToken") String attemptToken,
                                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_approval_workflow_binding
            SET status='RUNNING',process_instance_id=#{processInstanceId},started_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} AND status='STARTING'
              AND start_attempt_token=#{attemptToken}
            """)
    int markApprovalWorkflowRunning(@Param("tenantId") Long tenantId, @Param("approvalId") String approvalId,
                                    @Param("attemptToken") String attemptToken,
                                    @Param("processInstanceId") String processInstanceId,
                                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_approval_workflow_binding
            SET status='START_UNCERTAIN',last_error_code=#{errorCode},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId} AND status='STARTING'
              AND start_attempt_token=#{attemptToken}
            """)
    int markApprovalWorkflowStartUncertain(@Param("tenantId") Long tenantId,
                                           @Param("approvalId") String approvalId,
                                           @Param("attemptToken") String attemptToken,
                                           @Param("errorCode") String errorCode,
                                           @Param("now") LocalDateTime now);

    @TenantIgnore
    @Select("""
            SELECT approval_id,tenant_id,work_order_id,action_code,role_code,risk_level,requester_user_id,scope_hash,
                   process_definition_key,process_instance_id,business_key,status,last_bpm_status,last_reason_sha256,
                   terminal_operator_user_id,terminal_task_id,terminal_task_definition_key,
                   start_attempt_token,start_attempt_count,version,requested_at,start_attempted_at,started_at,
                   terminal_at,last_error_code,updated_at
            FROM cloudmold_agent_approval_workflow_binding
            WHERE process_definition_key=#{processDefinitionKey}
              AND (process_instance_id=#{processInstanceId} OR business_key=#{businessKey})
            LIMIT 1 FOR UPDATE
            """)
    ApprovalWorkflowBinding selectApprovalWorkflowBindingForEvent(
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("processInstanceId") String processInstanceId,
            @Param("businessKey") String businessKey);

    @Insert("""
            INSERT IGNORE INTO cloudmold_agent_approval_workflow_event
              (event_id,tenant_id,approval_id,process_instance_id,bpm_status,observed_status,reason_sha256,
               terminal_operator_user_id,terminal_task_id,terminal_task_definition_key,observed_at)
            VALUES (#{eventId},#{tenantId},#{approvalId},#{processInstanceId},#{bpmStatus},#{observedStatus},
                    #{reasonSha256},#{terminalOperatorUserId},#{terminalTaskId},#{terminalTaskDefinitionKey},
                    #{observedAt})
            """)
    int insertApprovalWorkflowEvent(ApprovalWorkflowEvent value);

    @Update("""
            UPDATE cloudmold_agent_approval_workflow_binding
            SET status=#{status},process_instance_id=COALESCE(process_instance_id,#{processInstanceId}),
                last_bpm_status=#{bpmStatus},last_reason_sha256=#{reasonSha256},terminal_at=#{now},
                terminal_operator_user_id=#{terminalOperatorUserId},
                terminal_task_id=#{terminalTaskId},
                terminal_task_definition_key=#{terminalTaskDefinitionKey},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId}
              AND status IN ('STARTING','RUNNING','START_UNCERTAIN')
              AND (process_instance_id IS NULL OR process_instance_id=#{processInstanceId})
            """)
    int markApprovalWorkflowTerminal(@Param("tenantId") Long tenantId,
                                     @Param("approvalId") String approvalId,
                                     @Param("processInstanceId") String processInstanceId,
                                     @Param("bpmStatus") Integer bpmStatus,
                                     @Param("status") String status,
                                     @Param("reasonSha256") String reasonSha256,
                                     @Param("terminalOperatorUserId") Long terminalOperatorUserId,
                                     @Param("terminalTaskId") String terminalTaskId,
                                     @Param("terminalTaskDefinitionKey") String terminalTaskDefinitionKey,
                                     @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_business_result
              (result_id,tenant_id,work_order_id,outcome_code,summary,evidence_ref,recorded_by_user_id,recorded_at)
            VALUES (#{resultId},#{tenantId},#{workOrderId},#{outcomeCode},#{summary},#{evidenceRef},
                    #{recordedByUserId},#{recordedAt})
            """)
    int insertBusinessResult(BusinessResult value);

    @Select("""
            SELECT result_id,tenant_id,work_order_id,outcome_code,summary,evidence_ref,recorded_by_user_id,recorded_at
            FROM cloudmold_agent_business_result WHERE tenant_id=#{tenantId} AND result_id=#{resultId}
            """)
    BusinessResult selectBusinessResult(@Param("tenantId") Long tenantId, @Param("resultId") String resultId);

    @Select("""
            SELECT result_id,tenant_id,work_order_id,outcome_code,summary,evidence_ref,recorded_by_user_id,recorded_at
            FROM cloudmold_agent_business_result
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} FOR UPDATE
            """)
    BusinessResult selectBusinessResultByWorkOrderForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("workOrderId") String workOrderId);

    @Insert("""
            INSERT INTO cloudmold_agent_execution_binding
              (binding_id,tenant_id,work_order_id,execution_generation,skill_task_id,skill_id,skill_version,
               skill_definition_closure_sha256,input_sha256,terminal_result_sha256,status,version,bound_at,
               accepted_at,updated_at)
            VALUES (#{bindingId},#{tenantId},#{workOrderId},#{executionGeneration},#{skillTaskId},#{skillId},
                    #{skillVersion},#{skillDefinitionClosureSha256},#{inputSha256},#{terminalResultSha256},
                    #{status},#{version},#{boundAt},#{acceptedAt},#{updatedAt})
            """)
    int insertExecutionBinding(ExecutionBinding value);

    @Select("""
            SELECT binding_id,tenant_id,work_order_id,execution_generation,skill_task_id,skill_id,skill_version,
                   skill_definition_closure_sha256,input_sha256,terminal_result_sha256,status,version,bound_at,
                   accepted_at,updated_at
            FROM cloudmold_agent_execution_binding
            WHERE tenant_id=#{tenantId} AND binding_id=#{bindingId} FOR UPDATE
            """)
    ExecutionBinding selectExecutionBindingForUpdate(@Param("tenantId") Long tenantId,
                                                      @Param("bindingId") String bindingId);

    @Select("""
            SELECT binding_id,tenant_id,work_order_id,execution_generation,skill_task_id,skill_id,skill_version,
                   skill_definition_closure_sha256,input_sha256,terminal_result_sha256,status,version,bound_at,
                   accepted_at,updated_at
            FROM cloudmold_agent_execution_binding
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId}
            ORDER BY execution_generation DESC LIMIT 1 FOR UPDATE
            """)
    ExecutionBinding selectLatestExecutionBindingForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("workOrderId") String workOrderId);

    @TenantIgnore
    @Select("""
            SELECT b.binding_id,b.tenant_id,w.assignee_user_id AS operator_user_id,b.skill_task_id,
                   b.skill_id,w.active_run_id AS run_id
            FROM cloudmold_agent_execution_binding b
            JOIN cloudmold_agent_work_order w
              ON w.tenant_id=b.tenant_id AND w.work_order_id=b.work_order_id
            WHERE b.status='BOUND' AND w.status='IN_PROGRESS'
            ORDER BY b.updated_at,b.binding_id LIMIT #{limit}
            """)
    List<ExecutionReconcileCandidate> selectExecutionReconcileCandidates(@Param("limit") int limit);

    @TenantIgnore
    @Select("""
            SELECT DISTINCT w.tenant_id,w.work_order_id,w.updated_at
            FROM cloudmold_agent_work_order w
            JOIN cloudmold_agent_business_mission m
              ON m.tenant_id=w.tenant_id AND m.mission_id=w.mission_id AND m.status='ACTIVE'
            LEFT JOIN cloudmold_agent_run_lease l
              ON l.tenant_id=w.tenant_id AND l.work_order_id=w.work_order_id AND l.status='ACTIVE'
            LEFT JOIN cloudmold_agent_mission_goal g
              ON g.tenant_id=w.tenant_id AND g.goal_id=w.goal_id AND g.status='ACTIVE'
            LEFT JOIN cloudmold_agent_work_dependency d
              ON d.tenant_id=w.tenant_id AND d.predecessor_work_order_id=w.work_order_id AND d.status='WAITING'
            WHERE w.status='COMPLETED'
              AND (l.work_order_id IS NOT NULL OR g.goal_id IS NOT NULL OR d.dependency_id IS NOT NULL)
            ORDER BY w.updated_at,w.work_order_id LIMIT #{limit}
            """)
    List<MissionResolutionCandidate> selectMissionResolutionCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_agent_execution_binding
            SET status='SUPERSEDED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND binding_id=#{bindingId} AND version=#{expectedVersion}
              AND status='BOUND'
            """)
    int supersedeExecutionBinding(@Param("tenantId") Long tenantId, @Param("bindingId") String bindingId,
                                  @Param("expectedVersion") Long expectedVersion,
                                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_execution_binding
            SET terminal_result_sha256=#{terminalResultSha256},status='EXECUTION_SUCCEEDED',accepted_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND binding_id=#{bindingId} AND version=#{expectedVersion}
              AND status='BOUND'
            """)
    int acceptExecutionBinding(@Param("tenantId") Long tenantId, @Param("bindingId") String bindingId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("terminalResultSha256") String terminalResultSha256,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_business_mission
              (mission_id,tenant_id,mission_type,template_version,title,objective_json,correlation_id,status,
               supervisor_user_id,version,started_at,deadline_at,completed_at,updated_at)
            VALUES (#{missionId},#{tenantId},#{missionType},#{templateVersion},#{title},CAST(#{objectiveJson} AS JSON),
                    #{correlationId},#{status},#{supervisorUserId},#{version},#{startedAt},#{deadlineAt},
                    #{completedAt},#{updatedAt})
            """)
    int insertMission(Mission value);

    @Select("SELECT * FROM cloudmold_agent_business_mission WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} FOR UPDATE")
    Mission selectMissionForUpdate(@Param("tenantId") Long tenantId, @Param("missionId") String missionId);

    @Insert("""
            INSERT INTO cloudmold_agent_mission_goal
              (goal_id,tenant_id,mission_id,goal_code,title,status,version,created_at,updated_at)
            VALUES (#{goalId},#{tenantId},#{missionId},#{goalCode},#{title},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMissionGoal(MissionGoal value);

    @Update("""
            UPDATE cloudmold_agent_mission_goal SET status='ACHIEVED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND goal_id=#{goalId} AND status='ACTIVE'
            """)
    int achieveMissionGoal(@Param("tenantId") Long tenantId,@Param("goalId") String goalId,
                           @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_work_dependency
              (dependency_id,tenant_id,mission_id,predecessor_work_order_id,successor_work_order_id,status,
               satisfied_by_result_id,version,satisfied_at,created_at,updated_at)
            VALUES (#{dependencyId},#{tenantId},#{missionId},#{predecessorWorkOrderId},#{successorWorkOrderId},
                    #{status},#{satisfiedByResultId},#{version},#{satisfiedAt},#{createdAt},#{updatedAt})
            """)
    int insertWorkDependency(WorkDependency value);

    @Select("""
            SELECT * FROM cloudmold_agent_work_dependency
            WHERE tenant_id=#{tenantId} AND predecessor_work_order_id=#{workOrderId} AND status='WAITING'
            ORDER BY dependency_id FOR UPDATE
            """)
    java.util.List<WorkDependency> selectWaitingDependenciesByPredecessor(@Param("tenantId") Long tenantId,
                                                                          @Param("workOrderId") String workOrderId);

    @Update("""
            UPDATE cloudmold_agent_work_dependency SET status='SATISFIED',satisfied_at=#{now},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND dependency_id=#{dependencyId} AND version=#{expectedVersion}
              AND status='WAITING'
            """)
    int satisfyDependency(@Param("tenantId") Long tenantId, @Param("dependencyId") String dependencyId,
                          @Param("expectedVersion") Long expectedVersion, @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_work_dependency
            WHERE tenant_id=#{tenantId} AND successor_work_order_id=#{workOrderId} AND status<>'SATISFIED'
            """)
    int countUnsatisfiedDependencies(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId);

    @Update("""
            UPDATE cloudmold_agent_role_handoff SET status='ACCEPTED',accepted_by_user_id=#{acceptedByUserId},
                accepted_at=#{now},version=version+1
            WHERE tenant_id=#{tenantId} AND work_order_id=#{sourceWorkOrderId}
              AND target_work_order_id=#{targetWorkOrderId} AND status='PENDING'
            """)
    int acceptSuccessorHandoff(@Param("tenantId") Long tenantId,
                               @Param("sourceWorkOrderId") String sourceWorkOrderId,
                               @Param("targetWorkOrderId") String targetWorkOrderId,
                               @Param("acceptedByUserId") Long acceptedByUserId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_agent_run_lease WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} FOR UPDATE")
    AgentRunLease selectRunLeaseForUpdate(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId);

    @Insert("""
            INSERT INTO cloudmold_agent_run_lease
              (tenant_id,work_order_id,mission_id,run_id,trigger_type,trigger_id,actor_user_id,role_code,
               lease_owner,lease_token,fencing_token,lease_until,status,version,started_at,updated_at)
            VALUES (#{tenantId},#{workOrderId},#{missionId},#{runId},#{triggerType},#{triggerId},#{actorUserId},
                    #{roleCode},#{leaseOwner},#{leaseToken},#{fencingToken},#{leaseUntil},#{status},#{version},
                    #{startedAt},#{updatedAt})
            ON DUPLICATE KEY UPDATE mission_id=VALUES(mission_id),run_id=VALUES(run_id),trigger_type=VALUES(trigger_type),
              trigger_id=VALUES(trigger_id),actor_user_id=VALUES(actor_user_id),role_code=VALUES(role_code),
              lease_owner=VALUES(lease_owner),lease_token=VALUES(lease_token),fencing_token=VALUES(fencing_token),
              lease_until=VALUES(lease_until),status=VALUES(status),version=version+1,started_at=VALUES(started_at),
              updated_at=VALUES(updated_at)
            """)
    int upsertRunLease(AgentRunLease value);

    @Insert("""
            INSERT INTO cloudmold_agent_run
              (run_id,tenant_id,mission_id,work_order_id,trigger_type,trigger_id,actor_user_id,role_code,
               fencing_token,status,started_at,completed_at,updated_at)
            VALUES (#{runId},#{tenantId},#{missionId},#{workOrderId},#{triggerType},#{triggerId},#{actorUserId},
                    #{roleCode},#{fencingToken},#{status},#{startedAt},#{completedAt},#{updatedAt})
            """)
    int insertAgentRun(AgentRun value);

    @Update("""
            UPDATE cloudmold_agent_run
            SET status=#{status},
                completed_at=CASE WHEN #{status}<>'ACTIVE' AND completed_at IS NULL THEN #{now} ELSE completed_at END,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND status='ACTIVE'
            """)
    int finishAgentRun(@Param("tenantId") Long tenantId,@Param("runId") String runId,
                       @Param("status") String status,@Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_mission_checkpoint
              (checkpoint_id,tenant_id,mission_id,work_order_id,run_id,fencing_token,decision_code,decision_json,
               decision_sha256,created_at)
            VALUES (#{checkpointId},#{tenantId},#{missionId},#{workOrderId},#{runId},#{fencingToken},#{decisionCode},
                    CAST(#{decisionJson} AS JSON),#{decisionSha256},#{createdAt})
            """)
    int insertMissionCheckpoint(MissionCheckpoint value);

    @Select("""
            SELECT checkpoint_id,tenant_id,mission_id,work_order_id,run_id,fencing_token,decision_code,
                   decision_json,decision_sha256,created_at
            FROM cloudmold_agent_mission_checkpoint
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND run_id=#{runId}
            ORDER BY created_at DESC,checkpoint_id DESC LIMIT 1 FOR UPDATE
            """)
    MissionCheckpoint selectMissionCheckpointForRunForUpdate(@Param("tenantId") Long tenantId,
                                                              @Param("workOrderId") String workOrderId,
                                                              @Param("runId") String runId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_work_dependency d
            JOIN cloudmold_agent_work_order successor
              ON successor.tenant_id=d.tenant_id AND successor.work_order_id=d.successor_work_order_id
            WHERE d.tenant_id=#{tenantId} AND d.predecessor_work_order_id=#{workOrderId}
              AND d.status='WAITING' AND successor.execution_required=b'1'
            """)
    int countWaitingExecutableSuccessors(@Param("tenantId") Long tenantId,
                                         @Param("workOrderId") String workOrderId);

    @Update("""
            UPDATE cloudmold_agent_work_order
            SET business_context_json=CAST(#{businessContextJson} AS JSON),
                execution_input_sha256=#{executionInputSha256},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND version=#{expectedVersion}
              AND status='WAITING_DEPENDENCY'
            """)
    int deriveSuccessorContext(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("businessContextJson") String businessContextJson,
                               @Param("executionInputSha256") String executionInputSha256,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_run_lease SET status='RELEASED',lease_until=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND run_id=#{runId}
              AND lease_owner=#{leaseOwner} AND lease_token=#{leaseToken} AND fencing_token=#{fencingToken}
              AND status='ACTIVE'
            """)
    int releaseRunLease(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                        @Param("runId") String runId, @Param("leaseOwner") String leaseOwner,
                        @Param("leaseToken") String leaseToken, @Param("fencingToken") Long fencingToken,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_agent_run_lease SET status='EXPIRED',version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId} AND run_id=#{runId}
              AND fencing_token=#{fencingToken} AND status='ACTIVE' AND lease_until<=#{now}
            """)
    int expireRunLease(@Param("tenantId") Long tenantId, @Param("workOrderId") String workOrderId,
                       @Param("runId") String runId, @Param("fencingToken") Long fencingToken,
                       @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_event_subscription
              (subscription_id,tenant_id,mission_id,work_order_id,event_type,schema_version,source_system,
               aggregate_type,aggregate_id,correlation_id,matcher_code,status,matched_event_id,
               matched_payload_sha256,matched_at,version,created_at,updated_at)
            VALUES (#{subscriptionId},#{tenantId},#{missionId},#{workOrderId},#{eventType},#{schemaVersion},
                    #{sourceSystem},#{aggregateType},#{aggregateId},#{correlationId},#{matcherCode},#{status},
                    #{matchedEventId},#{matchedPayloadSha256},#{matchedAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertEventSubscription(EventSubscription value);

    @Select("""
            SELECT * FROM cloudmold_agent_event_subscription
            WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND event_type=#{eventType} AND source_system=#{sourceSystem}
              AND schema_version=#{schemaVersion} AND aggregate_type=#{aggregateType} AND aggregate_id=#{aggregateId}
              AND (correlation_id IS NULL OR correlation_id=#{correlationId})
            ORDER BY subscription_id FOR UPDATE
            """)
    java.util.List<EventSubscription> selectMatchingSubscriptions(@Param("tenantId") Long tenantId,
        @Param("eventType") String eventType,@Param("schemaVersion") String schemaVersion,
        @Param("sourceSystem") String sourceSystem,
        @Param("aggregateType") String aggregateType,@Param("aggregateId") String aggregateId,
        @Param("correlationId") String correlationId);

    @Update("""
            UPDATE cloudmold_agent_event_subscription SET status='MATCHED',matched_event_id=#{eventId},
                matched_payload_sha256=#{payloadSha256},matched_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND subscription_id=#{subscriptionId} AND version=#{expectedVersion}
              AND status='ACTIVE'
            """)
    int matchSubscription(@Param("tenantId") Long tenantId,@Param("subscriptionId") String subscriptionId,
                          @Param("expectedVersion") Long expectedVersion,@Param("eventId") String eventId,
                          @Param("payloadSha256") String payloadSha256,@Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_metric_subscription
              (subscription_id,tenant_id,mission_id,work_order_id,metric_id,metric_version,dimension_hash,
               comparison_operator,threshold_value,unit_code,max_age_seconds,status,matched_observation_id,
               matched_value,matched_evidence_sha256,matched_at,version,created_at,updated_at)
            VALUES (#{subscriptionId},#{tenantId},#{missionId},#{workOrderId},#{metricId},#{metricVersion},
                    #{dimensionHash},#{comparisonOperator},#{thresholdValue},#{unitCode},#{maxAgeSeconds},
                    #{status},#{matchedObservationId},#{matchedValue},#{matchedEvidenceSha256},#{matchedAt},
                    #{version},#{createdAt},#{updatedAt})
            """)
    int insertMetricSubscription(MetricSubscription value);

    @Select("""
            SELECT * FROM cloudmold_agent_metric_subscription
            WHERE tenant_id=#{tenantId} AND status='ACTIVE' AND metric_id=#{metricId}
              AND metric_version=#{metricVersion} AND dimension_hash=#{dimensionHash} AND unit_code=#{unitCode}
            ORDER BY subscription_id FOR UPDATE
            """)
    List<MetricSubscription> selectMatchingMetricSubscriptions(
            @Param("tenantId") Long tenantId,
            @Param("metricId") String metricId,
            @Param("metricVersion") String metricVersion,
            @Param("dimensionHash") String dimensionHash,
            @Param("unitCode") String unitCode);

    @Update("""
            UPDATE cloudmold_agent_metric_subscription
            SET status='MATCHED',matched_observation_id=#{observationId},matched_value=#{value},
                matched_evidence_sha256=#{evidenceSha256},matched_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND subscription_id=#{subscriptionId} AND version=#{expectedVersion}
              AND status='ACTIVE'
            """)
    int matchMetricSubscription(
            @Param("tenantId") Long tenantId,
            @Param("subscriptionId") String subscriptionId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("observationId") String observationId,
            @Param("value") java.math.BigDecimal value,
            @Param("evidenceSha256") String evidenceSha256,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_mission_timer
              (timer_id,tenant_id,mission_id,work_order_id,timer_type,due_at,generation,status,fired_at,version,
               created_at,updated_at)
            VALUES (#{timerId},#{tenantId},#{missionId},#{workOrderId},#{timerType},#{dueAt},#{generation},#{status},
                    #{firedAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertMissionTimer(MissionTimer value);

    @Select("SELECT * FROM cloudmold_agent_mission_timer WHERE tenant_id=#{tenantId} AND timer_id=#{timerId} FOR UPDATE")
    MissionTimer selectMissionTimerForUpdate(@Param("tenantId") Long tenantId,@Param("timerId") String timerId);

    @TenantIgnore
    @Select("""
            SELECT * FROM cloudmold_agent_mission_timer
            WHERE status='SCHEDULED' AND due_at<=#{now} ORDER BY due_at,timer_id LIMIT #{limit}
            """)
    java.util.List<MissionTimer> selectDueMissionTimers(@Param("now") LocalDateTime now,@Param("limit") int limit);

    @TenantIgnore
    @Select("""
            SELECT w.* FROM cloudmold_agent_work_order w
            JOIN cloudmold_agent_run_lease l
              ON l.tenant_id=w.tenant_id AND l.work_order_id=w.work_order_id
            WHERE w.status='IN_PROGRESS' AND w.active_run_id=l.run_id
              AND l.status='ACTIVE' AND l.lease_until<=#{now}
            ORDER BY l.lease_until,w.work_order_id LIMIT #{limit}
            """)
    java.util.List<WorkOrder> selectExpiredRunLeaseWorkOrders(@Param("now") LocalDateTime now,
                                                              @Param("limit") int limit);

    @TenantIgnore
    @Select("""
            SELECT DISTINCT w.* FROM cloudmold_agent_work_order w
            JOIN cloudmold_agent_work_dependency d
              ON d.tenant_id=w.tenant_id AND d.predecessor_work_order_id=w.work_order_id AND d.status='WAITING'
            WHERE w.status='COMPLETED' ORDER BY w.updated_at,w.work_order_id LIMIT #{limit}
            """)
    java.util.List<WorkOrder> selectCompletedDependencySources(@Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_agent_mission_timer SET status='FIRED',fired_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND timer_id=#{timerId} AND version=#{expectedVersion}
              AND status='SCHEDULED' AND due_at<=#{now}
            """)
    int fireMissionTimer(@Param("tenantId") Long tenantId,@Param("timerId") String timerId,
                         @Param("expectedVersion") Long expectedVersion,@Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_outbox
              (event_id,tenant_id,aggregate_type,aggregate_id,event_type,payload_json,status,created_at)
            VALUES (#{eventId},#{tenantId},#{aggregateType},#{aggregateId},#{eventType},CAST(#{payloadJson} AS JSON),
                    'NEW',#{now})
            """)
    int insertAgentOutbox(@Param("eventId") String eventId,@Param("tenantId") Long tenantId,
                          @Param("aggregateType") String aggregateType,@Param("aggregateId") String aggregateId,
                          @Param("eventType") String eventType,@Param("payloadJson") String payloadJson,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT IGNORE INTO cloudmold_agent_event_inbox
              (tenant_id,event_id,event_type,payload_sha256,received_at)
            VALUES (#{tenantId},#{eventId},#{eventType},#{payloadSha256},#{now})
            """)
    int insertAgentEventInbox(@Param("tenantId") Long tenantId,@Param("eventId") String eventId,
                              @Param("eventType") String eventType,@Param("payloadSha256") String payloadSha256,
                              @Param("now") LocalDateTime now);

    @Select("""
            SELECT payload_sha256 FROM cloudmold_agent_event_inbox
            WHERE tenant_id=#{tenantId} AND event_id=#{eventId} FOR UPDATE
            """)
    String selectAgentEventInboxPayloadForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("eventId") String eventId);

    @Insert("""
            INSERT IGNORE INTO cloudmold_agent_metric_observation_inbox
              (tenant_id,observation_id,metric_id,metric_version,dimension_hash,metric_value,unit_code,
               source_query_id,evidence_sha256,observation_sha256,observed_at,received_at)
            VALUES (#{tenantId},#{observationId},#{metricId},#{metricVersion},#{dimensionHash},#{value},
                    #{unitCode},#{sourceQueryId},#{evidenceSha256},#{observationSha256},#{observedAt},#{receivedAt})
            """)
    int insertMetricObservation(MetricObservation value);

    @Select("""
            SELECT observation_sha256 FROM cloudmold_agent_metric_observation_inbox
            WHERE tenant_id=#{tenantId} AND observation_id=#{observationId} FOR UPDATE
            """)
    String selectMetricObservationHashForUpdate(@Param("tenantId") Long tenantId,
                                                 @Param("observationId") String observationId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_work_order
            WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} AND status<>'COMPLETED' AND status<>'CANCELLED'
            """)
    int countIncompleteMissionWorkOrders(@Param("tenantId") Long tenantId,@Param("missionId") String missionId);

    @Update("""
            UPDATE cloudmold_agent_business_mission SET status='COMPLETED',completed_at=#{now},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND mission_id=#{missionId} AND status='ACTIVE'
            """)
    int completeMission(@Param("tenantId") Long tenantId,@Param("missionId") String missionId,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_agent_audit_event
              (audit_event_id,tenant_id,aggregate_type,aggregate_id,aggregate_version,event_type,actor_user_id,
               detail_json,occurred_at,created_at)
            VALUES (#{auditEventId},#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{eventType},
                    #{actorUserId},CAST(#{detailJson} AS JSON),#{occurredAt},#{createdAt})
            """)
    int insertAuditEvent(AuditEvent value);
}
