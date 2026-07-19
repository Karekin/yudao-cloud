package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiWorkflowRunPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiWorkflowRunPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_ai_ops_workflow_run r
            WHERE r.tenant_id = #{tenantId}
            <if test="runId != null">AND r.run_id = #{runId}</if>
            <if test="runKey != null">AND r.run_key = #{runKey}</if>
            <if test="applicationId != null">AND r.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND r.workflow_id = #{workflowId}</if>
            <if test="triggerType != null">AND r.trigger_type = #{triggerType}</if>
            <if test="status != null">AND r.status = #{status}</if>
            <if test="createdAtFrom != null">AND r.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND r.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("runId") String runId,
                   @Param("runKey") String runKey,
                   @Param("applicationId") String applicationId,
                   @Param("workflowId") String workflowId,
                   @Param("triggerType") String triggerType,
                   @Param("status") String status,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT r.run_id,
                   r.run_key,
                   r.application_id,
                   r.workflow_id,
                   r.workflow_version,
                   r.trigger_type,
                   r.status,
                   r.error_code,
                   r.version AS aggregate_version,
                   r.started_at,
                   r.finished_at,
                   r.created_at,
                   r.updated_at
            FROM cloudmold_ai_ops_workflow_run r
            WHERE r.tenant_id = #{tenantId}
            <if test="runId != null">AND r.run_id = #{runId}</if>
            <if test="runKey != null">AND r.run_key = #{runKey}</if>
            <if test="applicationId != null">AND r.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND r.workflow_id = #{workflowId}</if>
            <if test="triggerType != null">AND r.trigger_type = #{triggerType}</if>
            <if test="status != null">AND r.status = #{status}</if>
            <if test="createdAtFrom != null">AND r.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND r.created_at &lt;= #{createdAtTo}</if>
            ORDER BY r.started_at DESC, r.run_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiWorkflowRunPageItem> selectPage(@Param("tenantId") Long tenantId,
                                            @Param("runId") String runId,
                                            @Param("runKey") String runKey,
                                            @Param("applicationId") String applicationId,
                                            @Param("workflowId") String workflowId,
                                            @Param("triggerType") String triggerType,
                                            @Param("status") String status,
                                            @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                            @Param("createdAtTo") LocalDateTime createdAtTo,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);
}
