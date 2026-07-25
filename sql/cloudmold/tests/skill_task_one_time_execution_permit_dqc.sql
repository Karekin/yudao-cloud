-- Every row returned by this query must have violation_count = 0.

SELECT 'skill_task_permit_without_task' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_skill_task_permit_consumption c
LEFT JOIN cloudmold_skill_task_instance t
  ON t.tenant_id = c.tenant_id AND t.task_id = c.task_id
WHERE t.task_id IS NULL
UNION ALL
SELECT 'skill_task_permit_scope_mismatch', COUNT(*)
FROM cloudmold_skill_task_permit_consumption c
JOIN cloudmold_skill_task_instance t
  ON t.tenant_id = c.tenant_id AND t.task_id = c.task_id
WHERE c.client_request_key <> t.client_request_key
   OR c.definition_closure_sha256 <> t.definition_closure_sha256
   OR c.input_sha256 <> t.input_sha256
   OR c.risk_level <> t.risk_level
UNION ALL
SELECT 'skill_task_high_risk_without_consumed_permit', COUNT(*)
FROM cloudmold_skill_task_instance t
LEFT JOIN cloudmold_skill_task_permit_consumption c
  ON c.tenant_id = t.tenant_id AND c.task_id = t.task_id
WHERE t.parent_task_id IS NULL
  AND t.risk_level IN ('R2','R3')
  AND t.approval_ref LIKE 'cma3:%'
  AND c.permit_id IS NULL
UNION ALL
SELECT 'skill_task_raw_permit_hash_mismatch', COUNT(*)
FROM cloudmold_skill_task_permit_consumption c
JOIN cloudmold_skill_task_instance t
  ON t.tenant_id = c.tenant_id AND t.task_id = c.task_id
WHERE LOWER(c.approval_ref_sha256) <> LOWER(SHA2(t.approval_ref, 256));
