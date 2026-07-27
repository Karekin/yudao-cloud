package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
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

    @Insert("""
            INSERT INTO cloudmold_ai_ops_temporal_schedule
            (tenant_id,schedule_id,display_name,description,skill_id,skill_version,input_json,
             interval_seconds,time_zone,overlap_policy,operator_user_id,operator_user_type,
             role_code,action_code,status,temporal_namespace,temporal_task_queue,version,created_at,updated_at)
            VALUES
            (#{tenantId},#{scheduleId},#{displayName},#{description},#{skillId},#{skillVersion},#{inputJson},
             #{intervalSeconds},#{timeZone},#{overlapPolicy},#{operatorUserId},#{operatorUserType},
             #{roleCode},#{actionCode},#{status},#{temporalNamespace},#{temporalTaskQueue},#{version},
             #{createdAt},#{updatedAt})
            """)
    int insertSchedule(TemporalScheduleRecord record);

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
             managed_run_id,skill_task_id,status,error_code,created_at,updated_at)
            VALUES
            (#{tenantId},#{temporalRunId},#{temporalWorkflowId},#{scheduleId},#{workOrderId},#{approvalId},
             #{managedRunId},#{skillTaskId},#{status},#{errorCode},#{createdAt},#{updatedAt})
            ON DUPLICATE KEY UPDATE status=VALUES(status),work_order_id=VALUES(work_order_id),
             approval_id=VALUES(approval_id),managed_run_id=VALUES(managed_run_id),
             skill_task_id=VALUES(skill_task_id),error_code=VALUES(error_code),updated_at=VALUES(updated_at)
            """)
    int upsertRunBinding(TemporalRunBindingRecord record);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_temporal_run_binding
            WHERE tenant_id=#{tenantId} AND work_order_id=#{workOrderId}
            """)
    TemporalRunBindingRecord selectRunBindingByWorkOrder(@Param("tenantId") Long tenantId,
                                                         @Param("workOrderId") String workOrderId);

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
            SET status=#{status},error_code=#{errorCode},managed_run_id=#{managedRunId},
                skill_task_id=#{skillTaskId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND temporal_run_id=#{temporalRunId}
            """)
    int updateRunBinding(@Param("tenantId") Long tenantId, @Param("temporalRunId") String temporalRunId,
                         @Param("status") String status, @Param("errorCode") String errorCode,
                         @Param("managedRunId") String managedRunId, @Param("skillTaskId") String skillTaskId,
                         @Param("now") LocalDateTime now);
}
