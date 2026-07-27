-- Agent Control is enabled in the CloudMold runtime. Reactivate its permission
-- records without exposing the former standalone navigation entry.
--
-- yudao derives effective permission codes only from enabled system_menu rows.
-- Keeping these hidden rows disabled therefore made granted query/command/govern
-- permissions disappear from the login session.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu
SET status = 0,
    visible = b'0',
    updater = 'CloudMold:agent-control-permission-activation',
    update_time = UTC_TIMESTAMP(6)
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND creator = 'CloudMold'
  AND deleted = b'0';

-- The BPM task is the approval authority. This template adds an in-app
-- notification so the assigned approver can discover the task from the bell.
INSERT INTO system_notify_template
  (name, code, nickname, content, type, params, status, remark,
   creator, create_time, updater, update_time, deleted)
SELECT 'BPM 待办任务提醒', 'bpm_task_assigned', '工作流程',
       '您收到流程「{processInstanceName}」的待办「{taskName}」，发起人：{startUserNickname}。办理地址：{detailUrl}',
       2,
       '["processInstanceName","taskName","startUserNickname","detailUrl"]',
       0,
       '工作流程待办分配后的站内信提醒',
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-approval-workflow', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM system_notify_template
  WHERE code = 'bpm_task_assigned' AND deleted = b'0'
);

UPDATE system_notify_template
SET name = 'BPM 待办任务提醒',
    nickname = '工作流程',
    content = '您收到流程「{processInstanceName}」的待办「{taskName}」，发起人：{startUserNickname}。办理地址：{detailUrl}',
    type = 2,
    params = '["processInstanceName","taskName","startUserNickname","detailUrl"]',
    status = 0,
    remark = '工作流程待办分配后的站内信提醒',
    updater = 'CloudMold:agent-approval-workflow',
    update_time = UTC_TIMESTAMP(6)
WHERE code = 'bpm_task_assigned' AND deleted = b'0';

UPDATE system_notify_message
SET template_nickname = '工作流程',
    template_content = CONCAT(
      '您收到流程「', JSON_UNQUOTE(JSON_EXTRACT(template_params, '$.processInstanceName')),
      '」的待办「', JSON_UNQUOTE(JSON_EXTRACT(template_params, '$.taskName')),
      '」，发起人：', JSON_UNQUOTE(JSON_EXTRACT(template_params, '$.startUserNickname')),
      '。办理地址：', JSON_UNQUOTE(JSON_EXTRACT(template_params, '$.detailUrl'))
    ),
    updater = 'CloudMold:agent-approval-workflow',
    update_time = UTC_TIMESTAMP(6)
WHERE template_code = 'bpm_task_assigned'
  AND deleted = b'0'
  AND JSON_VALID(template_params);

-- Make the system-owned model visible in the standard workflow model page.
-- The runtime registrar also creates this category lazily for future tenants.
INSERT INTO bpm_category
  (name, code, description, status, sort, creator, create_time,
   updater, update_time, deleted, tenant_id)
SELECT 'AI 运营审批', 'cloudmold-agent-control',
       'CloudMold Agent 运行中的人工审批流程',
       0, 5, 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-approval-workflow', UTC_TIMESTAMP(6), b'0', tenant.id
FROM system_tenant tenant
WHERE tenant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM bpm_category category
    WHERE category.tenant_id = tenant.id
      AND category.code = 'cloudmold-agent-control'
      AND category.deleted = b'0'
  );

UPDATE bpm_category
SET name = 'AI 运营审批',
    description = 'CloudMold Agent 运行中的人工审批流程',
    status = 0,
    sort = 5,
    updater = 'CloudMold:agent-approval-workflow',
    update_time = UTC_TIMESTAMP(6)
WHERE code = 'cloudmold-agent-control'
  AND updater = 'CloudMold:agent-approval-workflow'
  AND deleted = b'0';
