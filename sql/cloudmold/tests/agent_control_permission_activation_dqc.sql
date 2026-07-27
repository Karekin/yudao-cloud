-- Expected result: zero rows.
SELECT id, name, permission, type, status, visible, deleted
FROM system_menu
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND (
    deleted <> b'0'
    OR status <> 0
    OR visible <> b'0'
    OR permission NOT IN (
      'cloudmold:agent-control:query',
      'cloudmold:agent-control:command',
      'cloudmold:agent-control:govern'
    )
  )
UNION ALL
SELECT 0, 'missing Agent Control permission', '', 0, 0, b'0', b'0'
WHERE (
  SELECT COUNT(*)
  FROM system_menu
  WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
    AND deleted = b'0'
) <> 4;

-- Expected result: zero rows.
SELECT 0 AS id, 'missing BPM task-assigned notify template' AS name,
       '' AS permission, 0 AS type, 0 AS status, b'0' AS visible, b'0' AS deleted
WHERE (
  SELECT COUNT(*)
  FROM system_notify_template
  WHERE code = 'bpm_task_assigned'
    AND name = 'BPM 待办任务提醒'
    AND nickname = '工作流程'
    AND content = '您收到流程「{processInstanceName}」的待办「{taskName}」，发起人：{startUserNickname}。办理地址：{detailUrl}'
    AND status = 0
    AND deleted = b'0'
) <> 1;

-- Expected result: zero rows.
SELECT tenant.id, tenant.name, 'missing AI approval BPM category',
       0, 0, b'0', b'0'
FROM system_tenant tenant
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM bpm_category category
    WHERE category.tenant_id = tenant.id
      AND category.code = 'cloudmold-agent-control'
      AND category.status = 0
      AND category.deleted = b'0'
  );
