-- Register the Agent approval custom form as a hidden backend route.
--
-- yudao's default backend access mode only installs routes returned by
-- system_menu. A frontend-only route therefore cannot be opened from the BPM
-- model page even when the Vue component exists.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000074, 'Agent 审批表单', 'cloudmold:agent-control:query', 2, 999,
       9100000000000, 'agent-control/approval-form', '',
       'cloudmold/agent-control/approval-form', 'CloudMoldAgentApprovalForm',
       0, b'0', b'0', b'0', 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-approval-form-route', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu WHERE id = 9100000000074
);

UPDATE system_menu
SET name = 'Agent 审批表单',
    permission = 'cloudmold:agent-control:query',
    type = 2,
    sort = 999,
    parent_id = 9100000000000,
    path = 'agent-control/approval-form',
    icon = '',
    component = 'cloudmold/agent-control/approval-form',
    component_name = 'CloudMoldAgentApprovalForm',
    status = 0,
    visible = b'0',
    keep_alive = b'0',
    always_show = b'0',
    updater = 'CloudMold:agent-approval-form-route',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000074
  AND creator = 'CloudMold'
  AND deleted = b'0';

-- Any tenant package that can query Agent Control also receives the hidden
-- route. The route is never rendered in the sidebar because visible is false.
UPDATE system_tenant_package
SET menu_ids = CAST(
        JSON_ARRAY_APPEND(CAST(menu_ids AS JSON), '$', 9100000000074)
        AS CHAR CHARACTER SET utf8mb4
    ),
    updater = 'CloudMold:agent-approval-form-route',
    update_time = UTC_TIMESTAMP(6)
WHERE deleted = b'0'
  AND JSON_VALID(menu_ids)
  AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(9100000000071 AS JSON))
  AND NOT JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(9100000000074 AS JSON));

-- Mirror existing Agent Control query grants so current approvers and tenant
-- administrators can resolve the route without widening command permissions.
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time)
SELECT DISTINCT source.role_id, 9100000000074,
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-approval-form-route', UTC_TIMESTAMP(6)
FROM system_role_menu source
WHERE source.menu_id = 9100000000071
  AND NOT EXISTS (
      SELECT 1
      FROM system_role_menu existing
      WHERE existing.role_id = source.role_id
        AND existing.menu_id = 9100000000074
  );
