-- Every result column must be zero.

SELECT 'quality_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_quality_operation
WHERE status=10 AND (aggregate_type IS NULL OR aggregate_id IS NULL OR result_json IS NULL);

SELECT 'quality_standard_head_version_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_quality_standard standard
LEFT JOIN (
    SELECT tenant_id,standard_id,MAX(standard_version) max_version
    FROM cloudmold_quality_standard_version
    GROUP BY tenant_id,standard_id
) version
  ON version.tenant_id=standard.tenant_id AND version.standard_id=standard.standard_id
WHERE (standard.status='DRAFT' AND (standard.current_version<>0 OR version.max_version IS NOT NULL))
   OR (standard.status='PUBLISHED'
       AND (standard.current_version<1 OR version.max_version<>standard.current_version));

SELECT 'quality_standard_aggregate_version_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_quality_standard
WHERE aggregate_version<1 OR aggregate_version<current_version+1;

SELECT 'authenticator_certification_overlap' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_authenticator_certification a
JOIN cloudmold_authenticator_certification b
  ON b.tenant_id=a.tenant_id
 AND b.authenticator_principal_id=a.authenticator_principal_id
 AND b.standard_id=a.standard_id
 AND b.certification_id>a.certification_id
 AND a.status='ACTIVE' AND b.status='ACTIVE'
 AND a.effective_from<=b.effective_to AND b.effective_from<=a.effective_to;

SELECT 'inspection_task_standard_snapshot_missing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inspection_task task
LEFT JOIN cloudmold_quality_standard_version version
  ON version.tenant_id=task.tenant_id
 AND version.standard_id=task.standard_id
 AND version.standard_version=task.standard_version
 AND version.standard_version_id=task.standard_version_id
WHERE version.standard_version_id IS NULL;

SELECT 'inspection_task_history_version_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT task.tenant_id,task.task_id,task.version,
           COUNT(history.history_id) history_count,
           MIN(history.task_version) min_version,
           MAX(history.task_version) max_version
    FROM cloudmold_inspection_task task
    LEFT JOIN cloudmold_inspection_task_history history
      ON history.tenant_id=task.tenant_id AND history.task_id=task.task_id
    GROUP BY task.tenant_id,task.task_id,task.version
    HAVING history_count<>task.version OR min_version<>1 OR max_version<>task.version
) history_gap;

SELECT 'inspection_assignment_without_active_certification' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inspection_task task
WHERE task.status<>'CREATED'
  AND NOT EXISTS (
      SELECT 1 FROM cloudmold_authenticator_certification certification
      WHERE certification.tenant_id=task.tenant_id
        AND certification.authenticator_principal_id=task.authenticator_principal_id
        AND certification.standard_id=task.standard_id
        AND certification.effective_from<=DATE(task.assigned_at)
        AND certification.effective_to>=DATE(task.assigned_at)
  );

SELECT 'quality_capa_without_failed_task' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_quality_capa capa
LEFT JOIN cloudmold_inspection_task task
  ON task.tenant_id=capa.tenant_id AND task.task_id=capa.inspection_task_id
WHERE task.task_id IS NULL OR COALESCE(task.ground_truth_decision,task.decision)<>'FAIL';

SELECT 'quality_event_version_missing' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT operation.tenant_id,operation.aggregate_type,operation.aggregate_id,
           JSON_UNQUOTE(JSON_EXTRACT(operation.result_json,'$.aggregateVersion')) aggregate_version
    FROM cloudmold_quality_operation operation
    WHERE operation.status=10
) succeeded
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=succeeded.tenant_id
 AND event.aggregate_type COLLATE utf8mb4_unicode_ci=
     succeeded.aggregate_type COLLATE utf8mb4_unicode_ci
 AND event.aggregate_id COLLATE utf8mb4_unicode_ci=
     succeeded.aggregate_id COLLATE utf8mb4_unicode_ci
 AND event.aggregate_version=succeeded.aggregate_version
 AND event.source_system COLLATE utf8mb4_unicode_ci='cloudmold-quality'
WHERE event.event_id IS NULL;

SELECT 'inspection_reviewer_not_independent' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inspection_task
WHERE secondary_authenticator_principal_id=authenticator_principal_id
   OR adjudicator_principal_id=authenticator_principal_id
   OR adjudicator_principal_id=secondary_authenticator_principal_id;

SELECT 'inspection_recheck_state_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inspection_task
WHERE (status='RECHECK_REQUIRED' AND secondary_authenticator_principal_id IS NULL)
   OR (status='CONFLICTED'
       AND (secondary_decision IS NULL OR secondary_decision=decision
            OR ground_truth_decision IS NOT NULL))
   OR (ground_truth_decision IS NOT NULL
       AND (ground_truth_evidence_ref IS NULL
            OR (ground_truth_decision='FAIL' AND ground_truth_defect_code IS NULL)));

SELECT 'inspection_secondary_without_active_certification' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_inspection_task task
WHERE task.secondary_authenticator_principal_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM cloudmold_authenticator_certification certification
      WHERE certification.tenant_id=task.tenant_id
        AND certification.authenticator_principal_id=task.secondary_authenticator_principal_id
        AND certification.standard_id=task.standard_id
        AND certification.effective_from<=DATE(task.updated_at)
        AND certification.effective_to>=DATE(task.updated_at)
  );

SELECT 'quality_recall_without_final_failed_lot' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_quality_recall_action recall_action
LEFT JOIN cloudmold_inspection_task task
  ON task.tenant_id=recall_action.tenant_id
 AND task.task_id=recall_action.inspection_task_id
WHERE task.task_id IS NULL
   OR task.lot_id IS NULL
   OR COALESCE(task.ground_truth_decision,task.decision)<>'FAIL'
   OR task.lot_id<>recall_action.lot_id
   OR task.canonical_sku_id<>recall_action.canonical_sku_id;
