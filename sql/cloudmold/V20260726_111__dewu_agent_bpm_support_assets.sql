-- Register the BPM design-time assets used to govern the Agent approval flow
-- in the Dewu V1 tenant.
--
-- The running process continues to use its custom business form and the
-- built-in status-event callback. These rows make the approver pool, reusable
-- field schema, and optional terminal reconciliation listener visible and
-- maintainable from the standard BPM management pages.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @cloudmold_dewu_tenant_id := (
    SELECT id
    FROM system_tenant
    WHERE name = '得物V1'
      AND status = 0
      AND deleted = b'0'
    ORDER BY id
    LIMIT 1
);

SET @cloudmold_dewu_approver_id := (
    SELECT id
    FROM system_users
    WHERE tenant_id = @cloudmold_dewu_tenant_id
      AND username = 'aiopsapp162'
      AND status = 0
      AND deleted = b'0'
    ORDER BY id
    LIMIT 1
);

INSERT INTO bpm_user_group
    (name, description, user_ids, status, creator, create_time,
     updater, update_time, deleted, tenant_id)
SELECT 'Agent高风险动作审批组',
       'R2/R3 高风险动作的人工审批候选人池；成员由得物V1租户管理员维护。',
       CAST(JSON_ARRAY(@cloudmold_dewu_approver_id) AS CHAR CHARACTER SET utf8mb4),
       0, 'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-bpm-support', UTC_TIMESTAMP(6), b'0',
       @cloudmold_dewu_tenant_id
WHERE @cloudmold_dewu_tenant_id IS NOT NULL
  AND @cloudmold_dewu_approver_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM bpm_user_group
      WHERE tenant_id = @cloudmold_dewu_tenant_id
        AND name = 'Agent高风险动作审批组'
        AND deleted = b'0'
  );

UPDATE bpm_user_group
SET description = 'R2/R3 高风险动作的人工审批候选人池；成员由得物V1租户管理员维护。',
    user_ids = CAST(JSON_ARRAY(@cloudmold_dewu_approver_id) AS CHAR CHARACTER SET utf8mb4),
    status = 0,
    updater = 'CloudMold:agent-bpm-support',
    update_time = UTC_TIMESTAMP(6)
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批组'
  AND creator = 'CloudMold'
  AND @cloudmold_dewu_approver_id IS NOT NULL
  AND deleted = b'0';

INSERT INTO bpm_process_listener
    (name, type, status, event, value_type, value, creator, create_time,
     updater, update_time, deleted, tenant_id)
SELECT 'Agent审批终态对账监听模板',
       'execution', 0, 'end', 'expression',
       '${agentApprovalWorkflowTerminalDecisionReconciler.reconcile()}',
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-bpm-support', UTC_TIMESTAMP(6), b'0',
       @cloudmold_dewu_tenant_id
WHERE @cloudmold_dewu_tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM bpm_process_listener
      WHERE tenant_id = @cloudmold_dewu_tenant_id
        AND name = 'Agent审批终态对账监听模板'
        AND deleted = b'0'
  );

UPDATE bpm_process_listener
SET type = 'execution',
    status = 0,
    event = 'end',
    value_type = 'expression',
    value = '${agentApprovalWorkflowTerminalDecisionReconciler.reconcile()}',
    updater = 'CloudMold:agent-bpm-support',
    update_time = UTC_TIMESTAMP(6)
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent审批终态对账监听模板'
  AND creator = 'CloudMold'
  AND deleted = b'0';

SET @cloudmold_agent_form_conf := CAST(
    JSON_OBJECT(
        'form', JSON_OBJECT(
            'labelWidth', '140px',
            'labelPosition', 'right',
            'size', 'middle'
        ),
        'submitBtn', false,
        'resetBtn', false
    ) AS CHAR CHARACTER SET utf8mb4
);

SET @cloudmold_agent_form_fields := CAST(
    JSON_ARRAY(
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'businessTitle', 'title', '业务事项',
            '$required', true,
            'props', JSON_OBJECT('readonly', true, 'placeholder', '由 Agent 运行上下文自动填入')
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'riskLevel', 'title', '风险等级',
            '$required', true,
            'props', JSON_OBJECT('readonly', true, 'placeholder', 'R2 / R3')
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'roleCode', 'title', '执行岗位',
            '$required', true,
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'actionCode', 'title', '待执行动作',
            '$required', true,
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'textarea', 'field', 'impactScope', 'title', '影响范围',
            '$required', true,
            'props', JSON_OBJECT('readonly', true, 'rows', 3)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'requesterName', 'title', '申请人',
            '$required', true,
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'approverName', 'title', '审批人',
            '$required', true,
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'approvalId', 'title', '审批 ID',
            '$required', true,
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'input', 'field', 'processInstanceId', 'title', '流程实例',
            'props', JSON_OBJECT('readonly', true)
        ) AS CHAR CHARACTER SET utf8mb4),
        CAST(JSON_OBJECT(
            'type', 'textarea', 'field', 'businessDescription', 'title', '业务说明',
            '$required', true,
            'props', JSON_OBJECT('readonly', true, 'rows', 4)
        ) AS CHAR CHARACTER SET utf8mb4)
    ) AS CHAR CHARACTER SET utf8mb4
);

INSERT INTO bpm_form
    (name, status, conf, fields, remark, creator, create_time,
     updater, update_time, deleted, tenant_id)
SELECT 'Agent高风险动作审批字段模板',
       0,
       @cloudmold_agent_form_conf,
       @cloudmold_agent_form_fields,
       'Agent 审批使用自定义业务表单运行；本模板沉淀标准字段，供字段治理、预览和后续流程复用。',
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:agent-bpm-support', UTC_TIMESTAMP(6), b'0',
       @cloudmold_dewu_tenant_id
WHERE @cloudmold_dewu_tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM bpm_form
      WHERE tenant_id = @cloudmold_dewu_tenant_id
        AND name = 'Agent高风险动作审批字段模板'
        AND deleted = b'0'
  );

UPDATE bpm_form
SET status = 0,
    conf = @cloudmold_agent_form_conf,
    fields = @cloudmold_agent_form_fields,
    remark = 'Agent 审批使用自定义业务表单运行；本模板沉淀标准字段，供字段治理、预览和后续流程复用。',
    updater = 'CloudMold:agent-bpm-support',
    update_time = UTC_TIMESTAMP(6)
WHERE tenant_id = @cloudmold_dewu_tenant_id
  AND name = 'Agent高风险动作审批字段模板'
  AND creator = 'CloudMold'
  AND deleted = b'0';

