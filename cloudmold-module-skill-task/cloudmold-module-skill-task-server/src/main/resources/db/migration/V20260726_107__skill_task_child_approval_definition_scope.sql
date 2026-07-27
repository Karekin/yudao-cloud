-- Freeze the root approval definition closure on every composed child task.

ALTER TABLE cloudmold_skill_task_instance
  ADD COLUMN approval_scope_definition_closure_sha256 CHAR(64) NULL
    AFTER approval_scope_skill_version;

-- Existing composed tasks predate the dedicated approval-scope closure column.
-- Their immutable parent definition closure is the scope that authorized the
-- child submission, so preserve that scope deterministically during upgrade.
UPDATE cloudmold_skill_task_instance child
JOIN cloudmold_skill_task_instance parent
  ON parent.tenant_id = child.tenant_id
 AND parent.task_id = child.parent_task_id
SET child.approval_scope_definition_closure_sha256 =
    COALESCE(parent.approval_scope_definition_closure_sha256,
             parent.definition_closure_sha256)
WHERE child.parent_task_id IS NOT NULL
  AND child.risk_level IN ('R2', 'R3')
  AND child.approval_scope_definition_closure_sha256 IS NULL;

ALTER TABLE cloudmold_skill_task_instance
  ADD CONSTRAINT chk_skill_task_approval_scope_definition_closure_sha
    CHECK (
      approval_scope_definition_closure_sha256 IS NULL
      OR approval_scope_definition_closure_sha256 REGEXP '^[0-9a-f]{64}$'
    );
