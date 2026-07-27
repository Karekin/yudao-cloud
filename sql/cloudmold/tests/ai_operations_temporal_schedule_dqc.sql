SELECT 'invalid_schedule_interval' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_ai_ops_temporal_schedule
WHERE interval_seconds < 60;

SELECT 'approval_binding_missing_ids' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_ai_ops_temporal_run_binding
WHERE status = 'WAITING_APPROVAL'
  AND (work_order_id IS NULL OR approval_id IS NULL);

SELECT 'cross_tenant_schedule_binding' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_ai_ops_temporal_run_binding b
LEFT JOIN cloudmold_ai_ops_temporal_schedule s
  ON s.tenant_id = b.tenant_id AND s.schedule_id = b.schedule_id
WHERE s.schedule_id IS NULL;
