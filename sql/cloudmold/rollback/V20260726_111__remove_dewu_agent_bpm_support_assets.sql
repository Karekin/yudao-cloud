SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @cloudmold_dewu_tenant_id := (
    SELECT id
    FROM system_tenant
    WHERE name = '得物V1'
      AND deleted = b'0'
    ORDER BY id
    LIMIT 1
);

DELETE FROM bpm_user_group
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批组'
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:agent-bpm-support';

DELETE FROM bpm_process_listener
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent审批终态对账监听模板'
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:agent-bpm-support';

DELETE FROM bpm_form
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批字段模板'
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:agent-bpm-support';

