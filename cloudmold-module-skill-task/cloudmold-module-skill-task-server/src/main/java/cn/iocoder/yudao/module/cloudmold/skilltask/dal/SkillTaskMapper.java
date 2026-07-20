package cn.iocoder.yudao.module.cloudmold.skilltask.dal;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SkillTaskMapper {

    @Insert("""
            INSERT INTO cloudmold_skill_task_instance
              (tenant_id,task_id,run_id,skill_id,skill_version,client_request_key,input_json,input_sha256,
               risk_level,approval_ref,submitter_id,submitter_type,operator_id,operator_type,status,current_step_code,
               attempt_count,max_attempts,
               next_retry_at,version,created_at,updated_at)
            VALUES
              (#{tenantId},#{taskId},#{runId},#{skillId},#{skillVersion},#{clientRequestKey},#{inputJson},#{inputSha256},
               #{riskLevel},#{approvalRef},#{operatorId},#{operatorType},#{operatorId},#{operatorType},'QUEUED',
               #{currentStepCode},0,#{maxAttempts},
               #{now},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE task_id=task_id
            """)
    int insertTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                   @Param("runId") String runId, @Param("skillId") String skillId,
                   @Param("skillVersion") String skillVersion, @Param("clientRequestKey") String clientRequestKey,
                   @Param("inputJson") String inputJson, @Param("inputSha256") String inputSha256,
                   @Param("riskLevel") String riskLevel, @Param("approvalRef") String approvalRef,
                   @Param("operatorId") Long operatorId, @Param("operatorType") Integer operatorType,
                   @Param("currentStepCode") String currentStepCode, @Param("maxAttempts") Integer maxAttempts,
                   @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_skill_task_instance
              (tenant_id,task_id,run_id,skill_id,skill_version,client_request_key,input_json,input_sha256,
               risk_level,approval_ref,approval_scope_skill_id,approval_scope_skill_version,
               approval_scope_input_sha256,approval_scope_risk_level,parent_task_id,parent_step_code,
               submitter_id,submitter_type,operator_id,operator_type,status,current_step_code,
               attempt_count,max_attempts,next_retry_at,version,created_at,updated_at)
            VALUES
              (#{tenantId},#{taskId},#{runId},#{skillId},#{skillVersion},#{clientRequestKey},#{inputJson},#{inputSha256},
               #{riskLevel},#{approvalRef},#{approvalScopeSkillId},#{approvalScopeSkillVersion},
               #{approvalScopeInputSha256},#{approvalScopeRiskLevel},#{parentTaskId},#{parentStepCode},
               #{operatorId},#{operatorType},#{operatorId},#{operatorType},'QUEUED',#{currentStepCode},
               0,#{maxAttempts},#{now},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE task_id=task_id
            """)
    int insertChildTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                        @Param("runId") String runId, @Param("skillId") String skillId,
                        @Param("skillVersion") String skillVersion,
                        @Param("clientRequestKey") String clientRequestKey,
                        @Param("inputJson") String inputJson, @Param("inputSha256") String inputSha256,
                        @Param("riskLevel") String riskLevel, @Param("approvalRef") String approvalRef,
                        @Param("approvalScopeSkillId") String approvalScopeSkillId,
                        @Param("approvalScopeSkillVersion") String approvalScopeSkillVersion,
                        @Param("approvalScopeInputSha256") String approvalScopeInputSha256,
                        @Param("approvalScopeRiskLevel") String approvalScopeRiskLevel,
                        @Param("parentTaskId") String parentTaskId, @Param("parentStepCode") String parentStepCode,
                        @Param("operatorId") Long operatorId, @Param("operatorType") Integer operatorType,
                        @Param("currentStepCode") String currentStepCode, @Param("maxAttempts") Integer maxAttempts,
                        @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_skill_task_step
              (tenant_id,task_id,step_code,step_order,capability_id,operation_type,argument_template_json,
               idempotency_key,status,attempt_count,created_at,updated_at)
            VALUES
              (#{tenantId},#{taskId},#{stepCode},#{stepOrder},#{capabilityId},#{operationType},#{argumentTemplateJson},
               #{idempotencyKey},'PENDING',0,#{now},#{now})
            """)
    int insertStep(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                   @Param("stepCode") String stepCode, @Param("stepOrder") Integer stepOrder,
                   @Param("capabilityId") String capabilityId, @Param("operationType") String operationType,
                   @Param("argumentTemplateJson") String argumentTemplateJson,
                   @Param("idempotencyKey") String idempotencyKey, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_skill_task_step
              (tenant_id,task_id,step_code,step_order,step_kind,capability_id,operation_type,argument_template_json,
               child_skill_id,child_skill_version,child_run_id_template,poll_interval_seconds,
               idempotency_key,status,attempt_count,created_at,updated_at)
            VALUES
              (#{tenantId},#{taskId},#{stepCode},#{stepOrder},#{stepKind},NULL,'ORCHESTRATE',#{argumentTemplateJson},
               #{childSkillId},#{childSkillVersion},#{childRunIdTemplate},#{pollIntervalSeconds},
               #{idempotencyKey},'PENDING',0,#{now},#{now})
            """)
    int insertOrchestrationStep(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                                @Param("stepCode") String stepCode, @Param("stepOrder") Integer stepOrder,
                                @Param("stepKind") String stepKind,
                                @Param("argumentTemplateJson") String argumentTemplateJson,
                                @Param("childSkillId") String childSkillId,
                                @Param("childSkillVersion") String childSkillVersion,
                                @Param("childRunIdTemplate") String childRunIdTemplate,
                                @Param("pollIntervalSeconds") Integer pollIntervalSeconds,
                                @Param("idempotencyKey") String idempotencyKey,
                                @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_skill_task_step
              (tenant_id,task_id,step_code,step_order,step_kind,capability_id,operation_type,argument_template_json,
               poll_interval_seconds,wait_success_json,wait_failure_json,idempotency_key,status,attempt_count,
               created_at,updated_at)
            VALUES
              (#{tenantId},#{taskId},#{stepCode},#{stepOrder},'WAIT_CAPABILITY',#{capabilityId},'READ',
               #{argumentTemplateJson},#{pollIntervalSeconds},#{waitSuccessJson},#{waitFailureJson},
               #{idempotencyKey},'PENDING',0,#{now},#{now})
            """)
    int insertWaitCapabilityStep(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                                 @Param("stepCode") String stepCode, @Param("stepOrder") Integer stepOrder,
                                 @Param("capabilityId") String capabilityId,
                                 @Param("argumentTemplateJson") String argumentTemplateJson,
                                 @Param("pollIntervalSeconds") Integer pollIntervalSeconds,
                                 @Param("waitSuccessJson") String waitSuccessJson,
                                 @Param("waitFailureJson") String waitFailureJson,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_skill_task_history
              (tenant_id,task_id,aggregate_version,previous_status,current_status,current_step_code,
               detail_code,detail_message,actor_id,actor_type,occurred_at)
            VALUES
              (#{tenantId},#{taskId},#{aggregateVersion},#{previousStatus},#{currentStatus},#{currentStepCode},
               #{detailCode},#{detailMessage},#{actorId},#{actorType},#{now})
            """)
    int insertHistory(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                      @Param("aggregateVersion") Long aggregateVersion, @Param("previousStatus") String previousStatus,
                      @Param("currentStatus") String currentStatus, @Param("currentStepCode") String currentStepCode,
                      @Param("detailCode") String detailCode, @Param("detailMessage") String detailMessage,
                      @Param("actorId") Long actorId, @Param("actorType") Integer actorType,
                      @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
            """)
    Task selectTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId);

    @Select("""
            SELECT * FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
            FOR UPDATE
            """)
    Task selectTaskForUpdate(@Param("tenantId") Long tenantId, @Param("taskId") String taskId);

    @Select("""
            SELECT * FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId} AND skill_id=#{skillId} AND client_request_key=#{clientRequestKey}
            """)
    Task selectByRequestKey(@Param("tenantId") Long tenantId, @Param("skillId") String skillId,
                            @Param("clientRequestKey") String clientRequestKey);

    @Select("""
            SELECT * FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId} AND skill_id=#{skillId} AND client_request_key=#{clientRequestKey}
            FOR UPDATE
            """)
    Task selectByRequestKeyForUpdate(@Param("tenantId") Long tenantId, @Param("skillId") String skillId,
                                     @Param("clientRequestKey") String clientRequestKey);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET definition_sha256=#{definitionSha256},definition_closure_sha256=#{definitionClosureSha256},
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
              AND definition_sha256 IS NULL AND definition_closure_sha256 IS NULL
            """)
    int freezeDefinitionProof(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                              @Param("definitionSha256") String definitionSha256,
                              @Param("definitionClosureSha256") String definitionClosureSha256,
                              @Param("now") LocalDateTime now);

    @Select("""
            SELECT * FROM cloudmold_skill_task_step
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId}
            ORDER BY step_order
            """)
    List<Step> selectSteps(@Param("tenantId") Long tenantId, @Param("taskId") String taskId);

    @Select("""
            SELECT * FROM cloudmold_skill_task_instance
            WHERE tenant_id=#{tenantId} AND parent_task_id=#{parentTaskId}
            ORDER BY created_at,task_id
            """)
    List<Task> selectChildren(@Param("tenantId") Long tenantId, @Param("parentTaskId") String parentTaskId);

    @Select("""
            SELECT * FROM cloudmold_skill_task_step
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode}
            """)
    Step selectStep(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                    @Param("stepCode") String stepCode);

    @TenantIgnore
    @Select("""
            SELECT tenant_id,task_id,status,version,attempt_count,max_attempts
            FROM cloudmold_skill_task_instance
            WHERE next_retry_at<=#{now}
              AND (status='WAITING' OR (attempt_count<max_attempts
                AND (status='QUEUED' OR (status='RUNNING' AND lease_until<#{now}))))
            ORDER BY next_retry_at,updated_at,task_id LIMIT #{limit}
            """)
    List<Candidate> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @TenantIgnore
    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET status='RUNNING',attempt_count=attempt_count+CASE WHEN status='WAITING' THEN 0 ELSE 1 END,
                lease_owner=#{leaseOwner},lease_until=#{leaseUntil},
                started_at=COALESCE(started_at,#{now}),last_error_code=NULL,last_error_message=NULL,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND version=#{expectedVersion}
              AND next_retry_at<=#{now}
              AND (status='WAITING' OR (attempt_count<max_attempts
                AND (status='QUEUED' OR (status='RUNNING' AND lease_until<#{now}))))
            """)
    int claim(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
              @Param("expectedVersion") Long expectedVersion, @Param("leaseOwner") String leaseOwner,
              @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET lease_until=#{leaseUntil},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int renewLease(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                   @Param("leaseOwner") String leaseOwner, @Param("leaseUntil") LocalDateTime leaseUntil,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET status='RUNNING',attempt_count=attempt_count+CASE WHEN status='WAITING' THEN 0 ELSE 1 END,
                request_json=#{requestJson},request_sha256=#{requestSha256},
                started_at=COALESCE(started_at,#{now}),last_error_code=NULL,last_error_message=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode}
              AND status IN ('PENDING','RUNNING','WAITING','FAILED_RETRYABLE','NEEDS_REVIEW')
            """)
    int markStepRunning(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                        @Param("stepCode") String stepCode, @Param("requestJson") String requestJson,
                        @Param("requestSha256") String requestSha256, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET child_task_id=#{childTaskId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode}
              AND step_kind='SUBMIT_CHILD' AND status='RUNNING'
              AND (child_task_id IS NULL OR child_task_id=#{childTaskId})
            """)
    int bindChildTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                      @Param("stepCode") String stepCode, @Param("childTaskId") String childTaskId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET status='WAITING',result_json=COALESCE(#{resultJson},result_json),
                result_sha256=COALESCE(#{resultSha256},result_sha256),
                last_error_code=NULL,last_error_message=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode} AND status='RUNNING'
            """)
    int markStepWaiting(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                        @Param("stepCode") String stepCode, @Param("resultJson") String resultJson,
                        @Param("resultSha256") String resultSha256, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET status='SUCCEEDED',result_json=#{resultJson},result_sha256=#{resultSha256},
                last_error_code=NULL,last_error_message=NULL,completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode} AND status='RUNNING'
            """)
    int markStepSucceeded(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                          @Param("stepCode") String stepCode, @Param("resultJson") String resultJson,
                          @Param("resultSha256") String resultSha256, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET status=#{status},last_error_code=#{errorCode},last_error_message=#{errorMessage},updated_at=#{now}
                ,completed_at=CASE WHEN #{status}='NEEDS_REVIEW' THEN #{now} ELSE completed_at END
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND step_code=#{stepCode}
              AND status IN ('PENDING','RUNNING','FAILED_RETRYABLE','NEEDS_REVIEW')
            """)
    int markStepFailed(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                       @Param("stepCode") String stepCode, @Param("status") String status,
                       @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET current_step_code=#{nextStepCode},lease_until=#{leaseUntil},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int advance(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                @Param("leaseOwner") String leaseOwner, @Param("nextStepCode") String nextStepCode,
                @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET status='WAITING',next_retry_at=#{nextRetryAt},lease_owner=NULL,lease_until=NULL,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int waitTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                 @Param("leaseOwner") String leaseOwner, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET status='SUCCEEDED',current_step_code=NULL,lease_owner=NULL,lease_until=NULL,
                terminal_result_sha256=#{terminalResultSha256},
                next_retry_at=NULL,completed_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int complete(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                 @Param("leaseOwner") String leaseOwner,
                 @Param("terminalResultSha256") String terminalResultSha256,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET status=#{status},next_retry_at=#{nextRetryAt},lease_owner=NULL,lease_until=NULL,
                last_error_code=#{errorCode},last_error_message=#{errorMessage},
                completed_at=CASE WHEN #{status}='NEEDS_REVIEW' THEN #{now} ELSE completed_at END,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status='RUNNING' AND lease_owner=#{leaseOwner}
            """)
    int failTask(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                 @Param("leaseOwner") String leaseOwner, @Param("status") String status,
                 @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("errorCode") String errorCode,
                 @Param("errorMessage") String errorMessage, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_step
            SET status='PENDING',last_error_code=NULL,last_error_message=NULL,completed_at=NULL,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND status IN ('FAILED_RETRYABLE','NEEDS_REVIEW')
            """)
    int resetFailedSteps(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_skill_task_instance
            SET status='QUEUED',approval_ref=COALESCE(#{approvalRef},approval_ref),next_retry_at=#{now},
                operator_id=#{operatorId},operator_type=#{operatorType},
                max_attempts=GREATEST(max_attempts,attempt_count+1),lease_owner=NULL,lease_until=NULL,
                last_error_code=NULL,last_error_message=NULL,completed_at=NULL,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_id=#{taskId} AND version=#{expectedVersion}
              AND status='NEEDS_REVIEW'
            """)
    int retry(@Param("tenantId") Long tenantId, @Param("taskId") String taskId,
              @Param("expectedVersion") Long expectedVersion, @Param("approvalRef") String approvalRef,
              @Param("operatorId") Long operatorId, @Param("operatorType") Integer operatorType,
              @Param("now") LocalDateTime now);
}
