-- Every composed R2/R3 child submitted after definition-closure capture was
-- introduced must retain the root approval definition closure. Legacy tasks
-- whose parent predates both definition hashes remain auditable legacy rows;
-- inventing a current hash for them would falsely rewrite historical scope.
SELECT child.tenant_id, child.task_id, child.run_id, child.skill_id, child.risk_level
FROM cloudmold_skill_task_instance child
JOIN cloudmold_skill_task_instance parent
  ON parent.tenant_id = child.tenant_id
 AND parent.task_id = child.parent_task_id
WHERE child.parent_task_id IS NOT NULL
  AND child.risk_level IN ('R2', 'R3')
  AND COALESCE(parent.approval_scope_definition_closure_sha256,
               parent.definition_closure_sha256) IS NOT NULL
  AND (
    child.approval_scope_definition_closure_sha256 IS NULL
    OR child.approval_scope_definition_closure_sha256 NOT REGEXP '^[0-9a-f]{64}$'
  );
