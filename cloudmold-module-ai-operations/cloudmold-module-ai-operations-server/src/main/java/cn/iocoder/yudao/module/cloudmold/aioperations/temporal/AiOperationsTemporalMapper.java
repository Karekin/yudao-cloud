package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiOperationsTemporalMapper {

    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_schedule
            WHERE tenant_id=#{tenantId}
            ORDER BY created_at DESC
            """)
    List<TemporalScheduleRecord> selectSchedules(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_schedule
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            """)
    TemporalScheduleRecord selectSchedule(@Param("tenantId") Long tenantId,
                                          @Param("scheduleId") String scheduleId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_approval_policy
            WHERE tenant_id=#{tenantId} AND status='ACTIVE'
            """)
    TemporalApprovalPolicyRecord selectApprovalPolicy(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_role_definition
            WHERE tenant_id=#{tenantId} AND role_code=#{roleCode} AND status='ACTIVE'
            """)
    int countActiveAgentRole(@Param("tenantId") Long tenantId,
                             @Param("roleCode") String roleCode);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_role_action_policy
            WHERE tenant_id=#{tenantId} AND role_code=#{roleCode} AND action_code=#{actionCode}
              AND enabled=1 AND permission_mode='ALLOW'
            """)
    int countEnabledAgentActionPolicy(@Param("tenantId") Long tenantId,
                                      @Param("roleCode") String roleCode,
                                      @Param("actionCode") String actionCode);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_role_action_policy
            WHERE tenant_id=#{tenantId} AND role_code=#{roleCode} AND action_code=#{actionCode}
              AND enabled=1 AND permission_mode='ALLOW' AND approval_required=1 AND execution_required=1
              AND risk_level=#{riskLevel} AND skill_id=#{skillId} AND skill_version=#{skillVersion}
              AND skill_definition_closure_sha256=#{definitionClosureSha256}
            """)
    int countMatchingEnabledAgentActionPolicy(
            @Param("tenantId") Long tenantId,
            @Param("roleCode") String roleCode,
            @Param("actionCode") String actionCode,
            @Param("riskLevel") String riskLevel,
            @Param("skillId") String skillId,
            @Param("skillVersion") String skillVersion,
            @Param("definitionClosureSha256") String definitionClosureSha256);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId} AND actor_user_id=#{actorUserId} AND role_code=#{roleCode}
              AND status='ACTIVE' AND valid_from <= UTC_TIMESTAMP(6) AND valid_until > UTC_TIMESTAMP(6)
            """)
    int countEffectiveAgentRoleGrant(@Param("tenantId") Long tenantId,
                                     @Param("actorUserId") Long actorUserId,
                                     @Param("roleCode") String roleCode);

    @Select("""
            SELECT actor_user_id FROM cloudmold_agent_actor_role_grant
            WHERE tenant_id=#{tenantId} AND role_code=#{roleCode}
              AND status='ACTIVE' AND valid_from <= UTC_TIMESTAMP(6) AND valid_until > UTC_TIMESTAMP(6)
            ORDER BY actor_user_id
            LIMIT 1
            """)
    Long selectFirstEffectiveAgentRoleActor(@Param("tenantId") Long tenantId,
                                            @Param("roleCode") String roleCode);

    @Insert("""
            INSERT INTO cloudmold_ai_ops_temporal_schedule
            (tenant_id,schedule_id,display_name,description,skill_id,skill_version,input_json,input_strategy,
             interval_seconds,cron_expression,time_zone,overlap_policy,operator_user_id,operator_user_type,
             role_code,action_code,status,temporal_namespace,temporal_task_queue,version,created_at,updated_at)
            VALUES
            (#{tenantId},#{scheduleId},#{displayName},#{description},#{skillId},#{skillVersion},#{inputJson},
             #{inputStrategy},#{intervalSeconds},#{cronExpression},#{timeZone},#{overlapPolicy},
             #{operatorUserId},#{operatorUserType},
             #{roleCode},#{actionCode},#{status},#{temporalNamespace},#{temporalTaskQueue},#{version},
             #{createdAt},#{updatedAt})
            """)
    int insertSchedule(TemporalScheduleRecord record);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_schedule
            SET display_name=#{displayName},description=#{description},skill_version=#{skillVersion},
                input_json=#{inputJson},input_strategy=#{inputStrategy},interval_seconds=#{intervalSeconds},
                cron_expression=#{cronExpression},time_zone=#{timeZone},overlap_policy=#{overlapPolicy},
                operator_user_id=#{operatorUserId},operator_user_type=#{operatorUserType},
                role_code=#{roleCode},action_code=#{actionCode},status=#{status},
                temporal_namespace=#{temporalNamespace},temporal_task_queue=#{temporalTaskQueue},
                desired_policy_sha256=#{desiredPolicySha256},
                definition_closure_sha256=#{definitionClosureSha256},
                last_reconciled_at=#{lastReconciledAt},reconcile_error=#{reconcileError},
                version=version+1,updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            """)
    int updateScheduleDefinition(TemporalScheduleRecord record);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_schedule
            SET desired_policy_sha256=#{desiredPolicySha256},
                definition_closure_sha256=#{definitionClosureSha256},
                last_reconciled_at=#{lastReconciledAt},reconcile_error=NULL,
                version=version+1,updated_at=#{lastReconciledAt}
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            """)
    int markScheduleReconciled(@Param("tenantId") Long tenantId,
                               @Param("scheduleId") String scheduleId,
                               @Param("desiredPolicySha256") String desiredPolicySha256,
                               @Param("definitionClosureSha256") String definitionClosureSha256,
                               @Param("lastReconciledAt") LocalDateTime lastReconciledAt);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_schedule
            SET reconcile_error=#{error},last_reconciled_at=#{now},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            """)
    int markScheduleReconcileError(@Param("tenantId") Long tenantId,
                                   @Param("scheduleId") String scheduleId,
                                   @Param("error") String error,
                                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_schedule
            SET status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            """)
    int updateScheduleStatus(@Param("tenantId") Long tenantId, @Param("scheduleId") String scheduleId,
                             @Param("status") String status, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_ai_ops_temporal_run_binding
            (tenant_id,temporal_run_id,temporal_workflow_id,schedule_id,work_order_id,approval_id,
             managed_run_id,skill_task_id,status,error_code,business_reference_id,created_at,updated_at)
            VALUES
            (#{tenantId},#{temporalRunId},#{temporalWorkflowId},#{scheduleId},#{workOrderId},#{approvalId},
             #{managedRunId},#{skillTaskId},#{status},#{errorCode},#{businessReferenceId},#{createdAt},#{updatedAt})
            ON DUPLICATE KEY UPDATE status=VALUES(status),work_order_id=VALUES(work_order_id),
             approval_id=VALUES(approval_id),managed_run_id=VALUES(managed_run_id),
             skill_task_id=VALUES(skill_task_id),error_code=VALUES(error_code),
             business_reference_id=VALUES(business_reference_id),updated_at=VALUES(updated_at)
            """)
    int upsertRunBinding(TemporalRunBindingRecord record);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    TemporalRunBindingRecord selectRunBindingByWorkOrder(@Param("tenantId") Long tenantId,
                                                         @Param("workOrderId") String workOrderId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE tenant_id=#{tenantId} AND approval_id=#{approvalId}
            """)
    TemporalRunBindingRecord selectRunBindingByApproval(@Param("tenantId") Long tenantId,
                                                        @Param("approvalId") String approvalId);

    @TenantIgnore
    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE status='WAITING_APPROVAL'
            ORDER BY updated_at ASC
            LIMIT #{limit}
            """)
    List<TemporalRunBindingRecord> selectWaitingApprovalBindings(@Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='APPROVAL_SIGNALED',error_code=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
              AND status='WAITING_APPROVAL'
            """)
    int markApprovalSignaled(@Param("tenantId") Long tenantId,
                             @Param("temporalRunId") String temporalRunId,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='RECOVERY_STARTING',error_code='APPROVED_AFTER_TIMEOUT',updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
              AND status='WAITING_APPROVAL'
            """)
    int claimApprovedTimeoutRecovery(@Param("tenantId") Long tenantId,
                                     @Param("temporalRunId") String temporalRunId,
                                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='RECOVERY_DISPATCHED',error_code='APPROVED_AFTER_TIMEOUT',updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
              AND status='RECOVERY_STARTING'
            """)
    int markApprovedTimeoutRecoveryDispatched(@Param("tenantId") Long tenantId,
                                              @Param("temporalRunId") String temporalRunId,
                                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='WAITING_APPROVAL',error_code=#{errorCode},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
              AND status='RECOVERY_STARTING'
            """)
    int releaseApprovedTimeoutRecovery(@Param("tenantId") Long tenantId,
                                       @Param("temporalRunId") String temporalRunId,
                                       @Param("errorCode") String errorCode,
                                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status=#{status},error_code=#{errorCode},managed_run_id=#{managedRunId},
                skill_task_id=#{skillTaskId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
            """)
    int updateRunBinding(@Param("tenantId") Long tenantId, @Param("temporalRunId") String temporalRunId,
                         @Param("status") String status, @Param("errorCode") String errorCode,
                         @Param("managedRunId") String managedRunId, @Param("skillTaskId") String skillTaskId,
                         @Param("now") LocalDateTime now);

    // ===== 补货业务事件驱动通道（替换 24h 轮询） =====

    /** 进入/回到 WAITING_EVENT：物化 businessReferenceId（recommendationId），供事件桥接反查。 */
    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='WAITING_EVENT',business_reference_id=#{businessReferenceId},
                managed_run_id=#{managedRunId},skill_task_id=#{skillTaskId},error_code=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
            """)
    int markRunBindingWaitingEvent(@Param("tenantId") Long tenantId,
                                   @Param("temporalRunId") String temporalRunId,
                                   @Param("businessReferenceId") String businessReferenceId,
                                   @Param("managedRunId") String managedRunId,
                                   @Param("skillTaskId") String skillTaskId,
                                   @Param("now") LocalDateTime now);

    /** 按 businessReferenceId 反查等待中的运行（事件桥接发送 signal 的目标）。 */
    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE tenant_id=#{tenantId} AND business_reference_id=#{businessReferenceId}
              AND status='WAITING_EVENT'
            ORDER BY updated_at DESC
            """)
    List<TemporalRunBindingRecord> selectWaitingEventBindingsByBusinessRef(@Param("tenantId") Long tenantId,
                                                                           @Param("businessReferenceId") String businessReferenceId);

    /** 扫描所有 WAITING_EVENT 运行（reconciler 兜底用，跨租户）。 */
    @TenantIgnore
    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE status='WAITING_EVENT'
            ORDER BY updated_at ASC
            LIMIT #{limit}
            """)
    List<TemporalRunBindingRecord> selectWaitingEventBindings(@Param("limit") int limit);

    /** signal 发送成功后 CAS 标记，避免重复 signal；child 侧 fingerprint 去重双保险。 */
    @Update("""
            UPDATE cloudmold_ai_ops_temporal_run_binding
            SET status='BUSINESS_EVENT_SIGNALED',updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
              AND status='WAITING_EVENT'
            """)
    int markBusinessEventSignaled(@Param("tenantId") Long tenantId,
                                  @Param("temporalRunId") String temporalRunId,
                                  @Param("now") LocalDateTime now);

    // ===== 托管工作流每日发现候选 =====

    @Insert("""
            INSERT IGNORE INTO cloudmold_ai_ops_automation_candidate
            (tenant_id,candidate_id,client_request_key,skill_id,skill_version,business_key,input_json,
             due_at,status,version,created_at,updated_at)
            VALUES
            (#{tenantId},#{candidateId},#{clientRequestKey},#{skillId},#{skillVersion},#{businessKey},
             #{inputJson},#{dueAt},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertAutomationCandidate(TemporalAutomationCandidateRecord record);

    @Select("""
            SELECT input_json
            FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId}
              AND skill_id=#{skillId}
              AND status='SUCCEEDED'
            ORDER BY completed_at DESC,created_at DESC
            LIMIT 1
            """)
    String selectLatestSuccessfulSkillTaskInput(@Param("tenantId") Long tenantId,
                                                @Param("skillId") String skillId);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT s.result_json
            FROM cloudmold_skill_task_instance t
            JOIN cloudmold_skill_task_step s
              ON s.tenant_id=t.tenant_id AND s.task_id=t.task_id
            WHERE t.tenant_id=#{tenantId}
              AND t.skill_id=#{skillId}
              AND t.status='SUCCEEDED'
              AND s.step_code=#{stepCode}
              AND s.status='SUCCEEDED'
              AND s.result_json IS NOT NULL
            ORDER BY t.completed_at DESC,t.created_at DESC
            LIMIT 1
            """)
    String selectLatestSuccessfulSkillTaskStepResult(@Param("tenantId") Long tenantId,
                                                     @Param("skillId") String skillId,
                                                     @Param("stepCode") String stepCode);

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT source_inventory.warehouse_id AS source_warehouse_id,
                   target_warehouse.id AS target_warehouse_id,
                   source_inventory.sku_id AS wms_sku_id
            FROM wms_inventory source_inventory
            JOIN wms_warehouse source_warehouse
              ON source_warehouse.tenant_id=source_inventory.tenant_id
             AND source_warehouse.id=source_inventory.warehouse_id
             AND source_warehouse.deleted=0
            JOIN wms_item_sku sku
              ON sku.tenant_id=source_inventory.tenant_id
             AND sku.id=source_inventory.sku_id
             AND sku.deleted=0
            JOIN wms_warehouse target_warehouse
              ON target_warehouse.tenant_id=source_inventory.tenant_id
             AND target_warehouse.id<>source_inventory.warehouse_id
             AND target_warehouse.deleted=0
            LEFT JOIN wms_inventory target_inventory
              ON target_inventory.tenant_id=source_inventory.tenant_id
             AND target_inventory.warehouse_id=target_warehouse.id
             AND target_inventory.sku_id=source_inventory.sku_id
             AND target_inventory.deleted=0
            WHERE source_inventory.tenant_id=#{tenantId}
              AND source_inventory.deleted=0
              AND source_inventory.quantity>=5
            ORDER BY CASE WHEN target_inventory.id IS NULL THEN 1 ELSE 0 END,
                     source_inventory.quantity DESC,
                     source_inventory.warehouse_id ASC,
                     target_warehouse.id ASC
            LIMIT 1
            """)
    WmsTransferSeedRecord selectWmsTransferSeed(@Param("tenantId") Long tenantId);

    @InterceptorIgnore(tenantLine = "true") // Every table is tenant-scoped explicitly; JSqlParser cannot parse BINARY NOT EXISTS.
    @Select("""
            <script>
            SELECT o.tenant_id,o.event_id,o.event_type,o.schema_version,o.source_system,
                   o.aggregate_type,o.aggregate_id,o.aggregate_version,o.occurred_at,
                   o.correlation_id,o.causation_id,CAST(o.payload AS CHAR) AS payload,
                   o.payload_hash,o.recorded_at
            FROM cloudmold_event_outbox o
            WHERE o.tenant_id=#{tenantId}
              AND o.event_type IN
              <foreach collection="eventTypes" item="eventType" open="(" separator="," close=")">
                #{eventType}
              </foreach>
              AND NOT EXISTS (
                SELECT 1
                FROM cloudmold_ai_ops_automation_candidate_event ce
                WHERE ce.tenant_id=o.tenant_id
                  AND ce.consumer_id=#{consumerId}
                  AND BINARY ce.event_id=BINARY o.event_id
              )
            ORDER BY o.recorded_at ASC,o.event_id ASC
            LIMIT #{limit}
            </script>
            """)
    List<AutomationOutboxEventRecord> selectUnmaterializedAutomationEvents(
            @Param("tenantId") Long tenantId,
            @Param("consumerId") String consumerId,
            @Param("eventTypes") List<String> eventTypes,
            @Param("limit") int limit);

    @Insert("""
            INSERT IGNORE INTO cloudmold_ai_ops_automation_candidate_event
              (tenant_id,consumer_id,candidate_id,event_id,event_type,schema_version,
               source_system,aggregate_type,aggregate_id,aggregate_version,
               correlation_id,causation_id,payload_hash,disposition,rejection_reason,
               occurred_at,processed_at)
            VALUES
              (#{tenantId},#{consumerId},#{candidateId},#{eventId},#{eventType},#{schemaVersion},
               #{sourceSystem},#{aggregateType},#{aggregateId},#{aggregateVersion},
               #{correlationId},#{causationId},#{payloadHash},#{disposition},#{rejectionReason},
               #{occurredAt},#{processedAt})
            """)
    int insertAutomationCandidateEvent(AutomationCandidateEventRecord record);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_automation_candidate
            WHERE tenant_id=#{tenantId} AND skill_id=#{skillId} AND skill_version=#{skillVersion}
              AND due_at<=#{now}
              AND (status='PENDING' OR (status='CLAIMED' AND lease_until<#{now}))
            ORDER BY due_at ASC,created_at ASC
            LIMIT #{limit}
            """)
    List<TemporalAutomationCandidateRecord> selectDueAutomationCandidates(
            @Param("tenantId") Long tenantId,
            @Param("skillId") String skillId,
            @Param("skillVersion") String skillVersion,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_ai_ops_automation_candidate
            SET status='CLAIMED',lease_owner=#{leaseOwner},lease_until=#{leaseUntil},
                last_error=NULL,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
              AND (status='PENDING' OR (status='CLAIMED' AND lease_until<#{now}))
            """)
    int claimAutomationCandidate(@Param("tenantId") Long tenantId,
                                 @Param("candidateId") String candidateId,
                                 @Param("leaseOwner") String leaseOwner,
                                 @Param("leaseUntil") LocalDateTime leaseUntil,
                                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_automation_candidate
            SET status='DISPATCHED',temporal_workflow_id=#{workflowId},dispatched_at=#{now},
                lease_owner=NULL,lease_until=NULL,last_error=NULL,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
              AND status='CLAIMED' AND lease_owner=#{leaseOwner}
            """)
    int markAutomationCandidateDispatched(@Param("tenantId") Long tenantId,
                                          @Param("candidateId") String candidateId,
                                          @Param("leaseOwner") String leaseOwner,
                                          @Param("workflowId") String workflowId,
                                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_ai_ops_automation_candidate
            SET status='PENDING',lease_owner=NULL,lease_until=NULL,last_error=#{error},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND candidate_id=#{candidateId}
              AND status='CLAIMED' AND lease_owner=#{leaseOwner}
            """)
    int releaseAutomationCandidate(@Param("tenantId") Long tenantId,
                                   @Param("candidateId") String candidateId,
                                   @Param("leaseOwner") String leaseOwner,
                                   @Param("error") String error,
                                   @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_ai_ops_daily_dispatch_observation
              (tenant_id,schedule_id,temporal_run_id,business_date,outcome_code,
               candidate_count,dispatched_count,failed_count,workflow_ids_json,observed_at)
            VALUES
              (#{tenantId},#{scheduleId},#{temporalRunId},#{businessDate},#{outcomeCode},
               #{candidateCount},#{dispatchedCount},#{failedCount},
               CAST(#{workflowIdsJson} AS JSON),#{observedAt})
            ON DUPLICATE KEY UPDATE outcome_code=VALUES(outcome_code),
              candidate_count=VALUES(candidate_count),
              dispatched_count=VALUES(dispatched_count),
              failed_count=VALUES(failed_count),
              workflow_ids_json=VALUES(workflow_ids_json),
              observed_at=VALUES(observed_at)
            """)
    int insertDispatchObservation(TemporalDispatchObservationRecord record);

    @Select("""
            SELECT tenant_id,schedule_id,temporal_run_id,business_date,outcome_code,
                   candidate_count,dispatched_count,failed_count,
                   CAST(workflow_ids_json AS CHAR) AS workflow_ids_json,observed_at
            FROM cloudmold_ai_ops_daily_dispatch_observation
            WHERE tenant_id=#{tenantId} AND schedule_id=#{scheduleId}
            ORDER BY observed_at DESC,temporal_run_id DESC
            LIMIT 1
            """)
    TemporalDispatchObservationRecord selectLatestDispatchObservation(
            @Param("tenantId") Long tenantId,
            @Param("scheduleId") String scheduleId);

    @Select("""
            SELECT b.*
            FROM cloudmold_ai_ops_temporal_run_binding b
            WHERE b.tenant_id=#{tenantId} AND b.schedule_id=#{scheduleId}
            ORDER BY b.updated_at DESC,b.temporal_run_id DESC
            LIMIT 1
            """)
    TemporalRunBindingRecord selectLatestRunBindingForSchedule(
            @Param("tenantId") Long tenantId,
            @Param("scheduleId") String scheduleId);
}
