package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiOperationsConsoleQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_ai_ops_workflow_definition w
            JOIN cloudmold_ai_ops_application a
              ON a.tenant_id = w.tenant_id AND a.application_id = w.application_id
            WHERE w.tenant_id = #{tenantId}
            <if test="applicationId != null">AND a.application_id = #{applicationId}</if>
            <if test="applicationCode != null">AND a.application_code = #{applicationCode}</if>
            <if test="applicationStatus != null">AND a.status = #{applicationStatus}</if>
            <if test="workflowId != null">AND w.workflow_id = #{workflowId}</if>
            <if test="workflowCode != null">AND w.workflow_code = #{workflowCode}</if>
            </script>
            """)
    long countWorkflowPage(@Param("tenantId") Long tenantId,
                           @Param("applicationId") String applicationId,
                           @Param("applicationCode") String applicationCode,
                           @Param("applicationStatus") String applicationStatus,
                           @Param("workflowId") String workflowId,
                           @Param("workflowCode") String workflowCode);

    @Select("""
            <script>
            SELECT a.application_id,
                   a.application_code,
                   a.name AS application_name,
                   a.status AS application_status,
                   w.workflow_id,
                   w.workflow_code,
                   v.workflow_version_id,
                   v.workflow_version,
                   v.definition_ref,
                   v.definition_sha256,
                   v.published_at,
                   COALESCE(stats.run_count, 0) AS run_count,
                   COALESCE(stats.running_run_count, 0) AS running_run_count,
                   COALESCE(stats.succeeded_run_count, 0) AS succeeded_run_count,
                   COALESCE(stats.failed_run_count, 0) AS failed_run_count,
                   COALESCE(stats.cancelled_run_count, 0) AS cancelled_run_count,
                   last_run.run_id AS last_run_id,
                   last_run.status AS last_run_status,
                   last_run.started_at AS last_run_started_at,
                   last_run.finished_at AS last_run_finished_at
            FROM cloudmold_ai_ops_workflow_definition w
            JOIN cloudmold_ai_ops_application a
              ON a.tenant_id = w.tenant_id AND a.application_id = w.application_id
            JOIN cloudmold_ai_ops_workflow_version v
              ON v.tenant_id = w.tenant_id AND v.workflow_id = w.workflow_id AND v.workflow_version = w.current_version
            LEFT JOIN (
                SELECT tenant_id,
                       workflow_id,
                       COUNT(*) AS run_count,
                       SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running_run_count,
                       SUM(CASE WHEN status = 'SUCCEEDED' THEN 1 ELSE 0 END) AS succeeded_run_count,
                       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_run_count,
                       SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_run_count
                FROM cloudmold_ai_ops_workflow_run
                GROUP BY tenant_id, workflow_id
            ) stats
              ON stats.tenant_id = w.tenant_id AND stats.workflow_id = w.workflow_id
            LEFT JOIN (
                SELECT tenant_id, workflow_id, run_id, status, started_at, finished_at
                FROM (
                    SELECT tenant_id,
                           workflow_id,
                           run_id,
                           status,
                           started_at,
                           finished_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY tenant_id, workflow_id
                               ORDER BY started_at DESC, run_id DESC
                           ) AS rn
                    FROM cloudmold_ai_ops_workflow_run
                ) ranked
                WHERE ranked.rn = 1
            ) last_run
              ON last_run.tenant_id = w.tenant_id AND last_run.workflow_id = w.workflow_id
            WHERE w.tenant_id = #{tenantId}
            <if test="applicationId != null">AND a.application_id = #{applicationId}</if>
            <if test="applicationCode != null">AND a.application_code = #{applicationCode}</if>
            <if test="applicationStatus != null">AND a.status = #{applicationStatus}</if>
            <if test="workflowId != null">AND w.workflow_id = #{workflowId}</if>
            <if test="workflowCode != null">AND w.workflow_code = #{workflowCode}</if>
            ORDER BY COALESCE(last_run.started_at, v.published_at) DESC, w.workflow_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiWorkflowPageItem> selectWorkflowPage(@Param("tenantId") Long tenantId,
                                                @Param("applicationId") String applicationId,
                                                @Param("applicationCode") String applicationCode,
                                                @Param("applicationStatus") String applicationStatus,
                                                @Param("workflowId") String workflowId,
                                                @Param("workflowCode") String workflowCode,
                                                @Param("offset") long offset,
                                                @Param("limit") int limit);

    @Select("""
            SELECT r.run_id,
                   r.run_key,
                   r.application_id,
                   r.workflow_id,
                   r.workflow_version_id,
                   r.workflow_version,
                   r.trigger_type,
                   r.business_ref,
                   r.status,
                   r.error_code,
                   r.expected_invocation_count,
                   r.version AS aggregate_version,
                   r.started_at,
                   r.finished_at,
                   r.created_at,
                   r.updated_at,
                   a.application_code,
                   a.name AS application_name,
                   a.status AS application_status,
                   w.workflow_code,
                   w.current_version AS current_workflow_version,
                   v.definition_ref,
                   v.definition_sha256,
                   v.published_at
            FROM cloudmold_ai_ops_workflow_run r
            JOIN cloudmold_ai_ops_application a
              ON a.tenant_id = r.tenant_id AND a.application_id = r.application_id
            JOIN cloudmold_ai_ops_workflow_definition w
              ON w.tenant_id = r.tenant_id AND w.workflow_id = r.workflow_id
            JOIN cloudmold_ai_ops_workflow_version v
              ON v.tenant_id = r.tenant_id AND v.workflow_id = r.workflow_id
             AND v.workflow_version_id = r.workflow_version_id AND v.workflow_version = r.workflow_version
            WHERE r.tenant_id = #{tenantId} AND r.run_id = #{runId}
            """)
    AiWorkflowRunDetailRow selectRunDetail(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT attempt_id,
                   run_id,
                   run_key,
                   application_id,
                   workflow_id,
                   workflow_version,
                   step_ref,
                   attempt_no,
                   provider_code,
                   model_code,
                   outcome,
                   input_tokens,
                   cached_input_tokens,
                   output_tokens,
                   total_tokens,
                   latency_millis,
                   cost_amount_minor,
                   currency_code,
                   pricing_version_ref,
                   error_code,
                   occurred_at,
                   created_at
            FROM (
                SELECT a.attempt_id,
                       a.run_id,
                       r.run_key,
                       a.application_id,
                       a.workflow_id,
                       r.workflow_version,
                       a.step_ref,
                       a.attempt_no,
                       a.provider_code,
                       a.model_code,
                       a.outcome,
                       a.input_tokens,
                       a.cached_input_tokens,
                       a.output_tokens,
                       a.total_tokens,
                       a.latency_millis,
                       a.cost_amount_minor,
                       a.currency_code,
                       a.pricing_version_ref,
                       a.error_code,
                       a.occurred_at,
                       a.created_at
                FROM cloudmold_ai_ops_invocation_attempt a
                JOIN cloudmold_ai_ops_workflow_run r
                  ON r.tenant_id = a.tenant_id AND r.run_id = a.run_id
                WHERE a.tenant_id = #{tenantId} AND a.run_id = #{runId}
            ) detail
            ORDER BY occurred_at ASC, step_ref ASC, attempt_no ASC, attempt_id ASC
            """)
    List<AiObservationPageItem> selectRunInvocations(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT feedback_id,
                   feedback_type,
                   outcome_code,
                   evaluator_type,
                   evidence_ref,
                   occurred_at,
                   created_at
            FROM cloudmold_ai_ops_outcome_feedback
            WHERE tenant_id = #{tenantId} AND run_id = #{runId}
            ORDER BY occurred_at ASC, feedback_id ASC
            """)
    List<AiWorkflowRunFeedbackItem> selectRunFeedback(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT history_id,
                   operation_id,
                   operation_type,
                   aggregate_version,
                   previous_status,
                   current_status,
                   error_code,
                   occurred_at,
                   created_at
            FROM cloudmold_ai_ops_status_history
            WHERE tenant_id = #{tenantId}
              AND aggregate_type = 'ai_workflow_run'
              AND aggregate_id = #{runId}
            ORDER BY aggregate_version ASC, occurred_at ASC, history_id ASC
            """)
    List<AiWorkflowRunStatusHistoryItem> selectRunStatusHistory(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_ai_ops_outcome_feedback f
            JOIN cloudmold_ai_ops_workflow_run r
              ON r.tenant_id = f.tenant_id AND r.run_id = f.run_id
            WHERE f.tenant_id = #{tenantId}
              AND f.evidence_ref IS NOT NULL
            <if test="runId != null">AND f.run_id = #{runId}</if>
            <if test="applicationId != null">AND r.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND r.workflow_id = #{workflowId}</if>
            <if test="feedbackType != null">AND f.feedback_type = #{feedbackType}</if>
            <if test="outcomeCode != null">AND f.outcome_code = #{outcomeCode}</if>
            <if test="evaluatorType != null">AND f.evaluator_type = #{evaluatorType}</if>
            <if test="occurredAtFrom != null">AND f.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND f.occurred_at &lt;= #{occurredAtTo}</if>
            </script>
            """)
    long countArtifactPage(@Param("tenantId") Long tenantId,
                           @Param("runId") String runId,
                           @Param("applicationId") String applicationId,
                           @Param("workflowId") String workflowId,
                           @Param("feedbackType") String feedbackType,
                           @Param("outcomeCode") String outcomeCode,
                           @Param("evaluatorType") String evaluatorType,
                           @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                           @Param("occurredAtTo") LocalDateTime occurredAtTo);

    @Select("""
            <script>
            SELECT f.feedback_id,
                   f.run_id,
                   r.run_key,
                   r.application_id,
                   app.application_code,
                   r.workflow_id,
                   w.workflow_code,
                   r.workflow_version,
                   f.feedback_type,
                   f.outcome_code,
                   f.evaluator_type,
                   f.evidence_ref,
                   f.occurred_at,
                   f.created_at
            FROM cloudmold_ai_ops_outcome_feedback f
            JOIN cloudmold_ai_ops_workflow_run r
              ON r.tenant_id = f.tenant_id AND r.run_id = f.run_id
            JOIN cloudmold_ai_ops_application app
              ON app.tenant_id = r.tenant_id AND app.application_id = r.application_id
            JOIN cloudmold_ai_ops_workflow_definition w
              ON w.tenant_id = r.tenant_id AND w.workflow_id = r.workflow_id
            WHERE f.tenant_id = #{tenantId}
              AND f.evidence_ref IS NOT NULL
            <if test="runId != null">AND f.run_id = #{runId}</if>
            <if test="applicationId != null">AND r.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND r.workflow_id = #{workflowId}</if>
            <if test="feedbackType != null">AND f.feedback_type = #{feedbackType}</if>
            <if test="outcomeCode != null">AND f.outcome_code = #{outcomeCode}</if>
            <if test="evaluatorType != null">AND f.evaluator_type = #{evaluatorType}</if>
            <if test="occurredAtFrom != null">AND f.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND f.occurred_at &lt;= #{occurredAtTo}</if>
            ORDER BY f.occurred_at DESC, f.feedback_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiArtifactPageItem> selectArtifactPage(@Param("tenantId") Long tenantId,
                                                @Param("runId") String runId,
                                                @Param("applicationId") String applicationId,
                                                @Param("workflowId") String workflowId,
                                                @Param("feedbackType") String feedbackType,
                                                @Param("outcomeCode") String outcomeCode,
                                                @Param("evaluatorType") String evaluatorType,
                                                @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                                                @Param("occurredAtTo") LocalDateTime occurredAtTo,
                                                @Param("offset") long offset,
                                                @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_ai_ops_invocation_attempt a
            WHERE a.tenant_id = #{tenantId}
            <if test="runId != null">AND a.run_id = #{runId}</if>
            <if test="applicationId != null">AND a.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND a.workflow_id = #{workflowId}</if>
            <if test="stepRef != null">AND a.step_ref = #{stepRef}</if>
            <if test="providerCode != null">AND a.provider_code = #{providerCode}</if>
            <if test="modelCode != null">AND a.model_code = #{modelCode}</if>
            <if test="outcome != null">AND a.outcome = #{outcome}</if>
            <if test="occurredAtFrom != null">AND a.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND a.occurred_at &lt;= #{occurredAtTo}</if>
            </script>
            """)
    long countObservationPage(@Param("tenantId") Long tenantId,
                              @Param("runId") String runId,
                              @Param("applicationId") String applicationId,
                              @Param("workflowId") String workflowId,
                              @Param("stepRef") String stepRef,
                              @Param("providerCode") String providerCode,
                              @Param("modelCode") String modelCode,
                              @Param("outcome") String outcome,
                              @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                              @Param("occurredAtTo") LocalDateTime occurredAtTo);

    @Select("""
            <script>
            SELECT a.attempt_id,
                   a.run_id,
                   r.run_key,
                   a.application_id,
                   a.workflow_id,
                   r.workflow_version,
                   a.step_ref,
                   a.attempt_no,
                   a.provider_code,
                   a.model_code,
                   a.outcome,
                   a.input_tokens,
                   a.cached_input_tokens,
                   a.output_tokens,
                   a.total_tokens,
                   a.latency_millis,
                   a.cost_amount_minor,
                   a.currency_code,
                   a.pricing_version_ref,
                   a.error_code,
                   a.occurred_at,
                   a.created_at
            FROM cloudmold_ai_ops_invocation_attempt a
            JOIN cloudmold_ai_ops_workflow_run r
              ON r.tenant_id = a.tenant_id AND r.run_id = a.run_id
            WHERE a.tenant_id = #{tenantId}
            <if test="runId != null">AND a.run_id = #{runId}</if>
            <if test="applicationId != null">AND a.application_id = #{applicationId}</if>
            <if test="workflowId != null">AND a.workflow_id = #{workflowId}</if>
            <if test="stepRef != null">AND a.step_ref = #{stepRef}</if>
            <if test="providerCode != null">AND a.provider_code = #{providerCode}</if>
            <if test="modelCode != null">AND a.model_code = #{modelCode}</if>
            <if test="outcome != null">AND a.outcome = #{outcome}</if>
            <if test="occurredAtFrom != null">AND a.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND a.occurred_at &lt;= #{occurredAtTo}</if>
            ORDER BY a.occurred_at DESC, a.attempt_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiObservationPageItem> selectObservationPage(@Param("tenantId") Long tenantId,
                                                      @Param("runId") String runId,
                                                      @Param("applicationId") String applicationId,
                                                      @Param("workflowId") String workflowId,
                                                      @Param("stepRef") String stepRef,
                                                      @Param("providerCode") String providerCode,
                                                      @Param("modelCode") String modelCode,
                                                      @Param("outcome") String outcome,
                                                      @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                                                      @Param("occurredAtTo") LocalDateTime occurredAtTo,
                                                      @Param("offset") long offset,
                                                      @Param("limit") int limit);
}
