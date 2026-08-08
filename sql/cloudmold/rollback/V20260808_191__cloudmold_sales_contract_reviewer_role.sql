SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DELETE rm FROM system_role_menu rm
JOIN system_role r ON r.id=rm.role_id AND r.tenant_id=rm.tenant_id
WHERE r.code='sales_contract_reviewer'
  AND rm.updater='CloudMold:crm-sales-contract-review';

DELETE r FROM system_role r
WHERE r.code='sales_contract_reviewer'
  AND r.updater='CloudMold:crm-sales-contract-review'
  AND NOT EXISTS (SELECT 1 FROM system_user_role ur WHERE ur.role_id=r.id);
