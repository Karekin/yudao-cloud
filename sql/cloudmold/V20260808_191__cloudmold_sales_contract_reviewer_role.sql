SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Sales contract approval is a separation-of-duty role. Keep it distinct from
-- customer_sales_operator so a contract requester does not gain approval power.
INSERT INTO system_role
  (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT '销售合同审批人','sales_contract_reviewer',31,4,'',0,2,
       '独立复核 CloudMold 销售合同；不得与合同提交人使用同一身份',
       'CloudMold:crm-sales-contract-review',UTC_TIMESTAMP(6),
       'CloudMold:crm-sales-contract-review',UTC_TIMESTAMP(6),b'0',r.tenant_id
FROM system_role r
WHERE r.code='tenant_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role existing
    WHERE existing.tenant_id=r.tenant_id AND existing.code='sales_contract_reviewer'
      AND existing.deleted=b'0'
  );

-- Expose only the sales-contract read surface and the assignee's BPM todo
-- approval action. Business commands remain owned by customer_sales_operator.
INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT r.id,m.id,r.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:crm-sales-contract-review',UTC_TIMESTAMP(6),b'0'
FROM system_role r
JOIN system_menu m ON m.id IN (
  1185,1200,1207,1222,
  9100000000180,9100000000807,9100000000852
) AND m.deleted=b'0'
LEFT JOIN system_role_menu rm
  ON rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
WHERE r.code='sales_contract_reviewer' AND r.deleted=b'0' AND rm.menu_id IS NULL;
