package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkflowEvidenceQueryMapper {

    @Select("""
            SELECT COUNT(*) FROM (
                SELECT observation_id AS record_id
                  FROM cloudmold_ai_ops_workflow_observation
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT problem_id AS record_id
                  FROM cloudmold_ai_ops_workflow_problem
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT feedback_id AS record_id
                  FROM cloudmold_ai_ops_workflow_user_feedback
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT snapshot_id AS record_id
                  FROM cloudmold_ai_ops_external_evidence_snapshot
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
            ) all_rows
            """)
    Long countTimeline(@Param("tenantId") Long tenantId,
                       @Param("workflowId") String workflowId,
                       @Param("windowStart") LocalDateTime windowStart,
                       @Param("windowEnd") LocalDateTime windowEnd);

    @Select("""
            SELECT * FROM (
                SELECT 'OBSERVATION' AS record_type,
                       observation_id AS record_id,
                       lineage_id,
                       workflow_id,
                       workflow_version,
                       proposal_id,
                       source_type,
                       headline,
                       detail_text,
                       model_summary,
                       evidence_ref,
                       NULL AS external_url,
                       summary_source_refs_json,
                       corroborating_source_types_json,
                       metrics_json,
                       severity,
                       dqc_status,
                       record_status,
                       release_eligible,
                       fresh_until,
                       window_start,
                       window_end,
                       observed_at AS recorded_at,
                       actor_subject,
                       NULL AS source_class,
                       NULL AS confidence,
                       NULL AS region,
                       NULL AS applicability,
                       NULL AS content_hash_sha256
                  FROM cloudmold_ai_ops_workflow_observation
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT 'PROBLEM' AS record_type,
                       problem_id AS record_id,
                       lineage_id,
                       workflow_id,
                       workflow_version,
                       proposal_id,
                       source_type,
                       headline,
                       problem_detail AS detail_text,
                       model_summary,
                       evidence_ref,
                       NULL AS external_url,
                       summary_source_refs_json,
                       corroborating_source_types_json,
                       metrics_json,
                       severity,
                       dqc_status,
                       record_status,
                       release_eligible,
                       fresh_until,
                       window_start,
                       window_end,
                       observed_at AS recorded_at,
                       actor_subject,
                       NULL AS source_class,
                       NULL AS confidence,
                       NULL AS region,
                       NULL AS applicability,
                       NULL AS content_hash_sha256
                  FROM cloudmold_ai_ops_workflow_problem
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT 'FEEDBACK' AS record_type,
                       feedback_id AS record_id,
                       lineage_id,
                       workflow_id,
                       workflow_version,
                       proposal_id,
                       source_type,
                       feedback_label AS headline,
                       feedback_text AS detail_text,
                       model_summary,
                       evidence_ref,
                       NULL AS external_url,
                       summary_source_refs_json,
                       corroborating_source_types_json,
                       metrics_json,
                       severity,
                       dqc_status,
                       record_status,
                       release_eligible,
                       fresh_until,
                       window_start,
                       window_end,
                       observed_at AS recorded_at,
                       actor_subject,
                       feedback_type AS source_class,
                       NULL AS confidence,
                       NULL AS region,
                       NULL AS applicability,
                       NULL AS content_hash_sha256
                  FROM cloudmold_ai_ops_workflow_user_feedback
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
                UNION ALL
                SELECT 'EXTERNAL_SNAPSHOT' AS record_type,
                       snapshot_id AS record_id,
                       lineage_id,
                       workflow_id,
                       workflow_version,
                       proposal_id,
                       source_type,
                       source_class AS headline,
                       summary_text AS detail_text,
                       NULL AS model_summary,
                       NULL AS evidence_ref,
                       url AS external_url,
                       NULL AS summary_source_refs_json,
                       NULL AS corroborating_source_types_json,
                       NULL AS metrics_json,
                       severity,
                       dqc_status,
                       record_status,
                       release_eligible,
                       fresh_until,
                       window_start,
                       window_end,
                       fetched_at AS recorded_at,
                       actor_subject,
                       source_class,
                       confidence,
                       region,
                       applicability,
                       content_hash_sha256
                  FROM cloudmold_ai_ops_external_evidence_snapshot
                 WHERE tenant_id=#{tenantId}
                   AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart}
                   AND window_start <= #{windowEnd}
            ) timeline
            ORDER BY recorded_at DESC, record_type DESC, record_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<WorkflowEvidenceTimelineRow> selectTimeline(@Param("tenantId") Long tenantId,
                                                     @Param("workflowId") String workflowId,
                                                     @Param("windowStart") LocalDateTime windowStart,
                                                     @Param("windowEnd") LocalDateTime windowEnd,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    @Select("""
            SELECT bucket_start,
                   DATE_ADD(bucket_start, INTERVAL 1 DAY) AS bucket_end,
                   COUNT(*) AS total_count,
                   SUM(record_type='OBSERVATION') AS observation_count,
                   SUM(record_type='PROBLEM') AS problem_count,
                   SUM(record_type='FEEDBACK') AS feedback_count,
                   SUM(record_type='EXTERNAL_SNAPSHOT') AS external_snapshot_count,
                   SUM(release_eligible = 1) AS release_eligible_count,
                   SUM(severity='CRITICAL') AS critical_count,
                   SUM(dqc_status='FAIL') AS dqc_fail_count,
                   SUM(fresh_until IS NOT NULL AND fresh_until < UTC_TIMESTAMP(6)) AS stale_count,
                   SUM(record_type='PROBLEM' AND record_status='OPEN') AS open_problem_count
              FROM (
                SELECT DATE(window_start) AS bucket_start, 'OBSERVATION' AS record_type, severity, dqc_status,
                       record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_observation
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE(window_start) AS bucket_start, 'PROBLEM' AS record_type, severity, dqc_status,
                       record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_problem
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE(window_start) AS bucket_start, 'FEEDBACK' AS record_type, severity, dqc_status,
                       record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_user_feedback
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE(window_start) AS bucket_start, 'EXTERNAL_SNAPSHOT' AS record_type, severity, dqc_status,
                       record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_external_evidence_snapshot
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
            ) digest
            GROUP BY bucket_start
            ORDER BY bucket_start DESC
            """)
    List<WorkflowEvidenceDigestRow> selectDailyDigest(@Param("tenantId") Long tenantId,
                                                      @Param("workflowId") String workflowId,
                                                      @Param("windowStart") LocalDateTime windowStart,
                                                      @Param("windowEnd") LocalDateTime windowEnd);

    @Select("""
            SELECT bucket_start,
                   DATE_ADD(bucket_start, INTERVAL 7 DAY) AS bucket_end,
                   COUNT(*) AS total_count,
                   SUM(record_type='OBSERVATION') AS observation_count,
                   SUM(record_type='PROBLEM') AS problem_count,
                   SUM(record_type='FEEDBACK') AS feedback_count,
                   SUM(record_type='EXTERNAL_SNAPSHOT') AS external_snapshot_count,
                   SUM(release_eligible = 1) AS release_eligible_count,
                   SUM(severity='CRITICAL') AS critical_count,
                   SUM(dqc_status='FAIL') AS dqc_fail_count,
                   SUM(fresh_until IS NOT NULL AND fresh_until < UTC_TIMESTAMP(6)) AS stale_count,
                   SUM(record_type='PROBLEM' AND record_status='OPEN') AS open_problem_count
              FROM (
                SELECT DATE_SUB(DATE(window_start), INTERVAL WEEKDAY(window_start) DAY) AS bucket_start,
                       'OBSERVATION' AS record_type, severity, dqc_status, record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_observation
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE_SUB(DATE(window_start), INTERVAL WEEKDAY(window_start) DAY) AS bucket_start,
                       'PROBLEM' AS record_type, severity, dqc_status, record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_problem
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE_SUB(DATE(window_start), INTERVAL WEEKDAY(window_start) DAY) AS bucket_start,
                       'FEEDBACK' AS record_type, severity, dqc_status, record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_workflow_user_feedback
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
                UNION ALL
                SELECT DATE_SUB(DATE(window_start), INTERVAL WEEKDAY(window_start) DAY) AS bucket_start,
                       'EXTERNAL_SNAPSHOT' AS record_type, severity, dqc_status, record_status, release_eligible, fresh_until
                  FROM cloudmold_ai_ops_external_evidence_snapshot
                 WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId}
                   AND window_end >= #{windowStart} AND window_start <= #{windowEnd}
            ) digest
            GROUP BY bucket_start
            ORDER BY bucket_start DESC
            """)
    List<WorkflowEvidenceDigestRow> selectWeeklyDigest(@Param("tenantId") Long tenantId,
                                                       @Param("workflowId") String workflowId,
                                                       @Param("windowStart") LocalDateTime windowStart,
                                                       @Param("windowEnd") LocalDateTime windowEnd);
}
