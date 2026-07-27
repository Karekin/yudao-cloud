-- Restore the pre-activation state while keeping the permission records.

UPDATE system_menu
SET status = 1,
    visible = b'0',
    updater = 'CloudMold:agent-control-permission-activation:rollback',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND creator = 'CloudMold'
  AND deleted = b'0';

DELETE FROM system_notify_template
WHERE code = 'bpm_task_assigned'
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:agent-approval-workflow';

DELETE FROM bpm_category
WHERE code = 'cloudmold-agent-control'
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:agent-approval-workflow'
  AND NOT EXISTS (
    SELECT 1 FROM ACT_RE_MODEL model
    WHERE model.TENANT_ID_ = CAST(bpm_category.tenant_id AS CHAR)
      AND model.CATEGORY_ = bpm_category.code
  );
