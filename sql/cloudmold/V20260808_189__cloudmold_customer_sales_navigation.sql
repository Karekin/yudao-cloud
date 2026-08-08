SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Repair the short-lived pre-release allocation that overlapped existing
-- supply-chain, finance and DreamPlant menu ids. Only rows authored by this
-- migration are eligible for cleanup; existing menus in the collided range
-- remain untouched.
DELETE rm FROM system_role_menu rm
JOIN system_menu m ON m.id=rm.menu_id
WHERE m.id IN (9100000000500,9100000000501,9100000000502,
               9100000000553,9100000000554,9100000000555)
  AND m.updater='CloudMold:crm-navigation';
DELETE FROM system_menu
WHERE id IN (9100000000500,9100000000501,9100000000502,
             9100000000553,9100000000554,9100000000555)
  AND updater='CloudMold:crm-navigation';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,
   always_show,creator,create_time,updater,update_time,deleted)
SELECT 9100000000180,'客户与销售','',1,6,9100000000000,'customer-sales','lucide:users-round',NULL,NULL,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),'CloudMold:crm-navigation',UTC_TIMESTAMP(6),b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id=9100000000180);

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,
   always_show,creator,create_time,updater,update_time,deleted)
SELECT c.id,c.name,c.permission,2,c.sort,9100000000180,c.path,c.icon,c.component,c.component_name,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),'CloudMold:crm-navigation',UTC_TIMESTAMP(6),b'0'
FROM (
  SELECT 9100000000800 id,'销售工作台' name,'cloudmold:crm:query' permission,1 sort,'workbench' path,
    'lucide:gauge' icon,'cloudmold/crm/workbench/index' component,'CloudMoldCrmWorkbench' component_name
  UNION ALL SELECT 9100000000801,'线索','cloudmold:crm:query',2,'leads','lucide:radar',
    'cloudmold/crm/clue/index','CloudMoldCrmLead'
  UNION ALL SELECT 9100000000802,'客户','cloudmold:crm:query',3,'customers','lucide:building-2',
    'cloudmold/crm/customer/index','CloudMoldCrmCustomer'
  UNION ALL SELECT 9100000000803,'联系人','cloudmold:crm:query',4,'contacts','lucide:contact',
    'cloudmold/crm/contact/index','CloudMoldCrmContact'
  UNION ALL SELECT 9100000000804,'客户公海','cloudmold:crm:query',5,'pool','lucide:waves',
    'cloudmold/crm/customer/pool/index','CloudMoldCrmCustomerPool'
  UNION ALL SELECT 9100000000805,'跟进记录','cloudmold:crm:query',6,'follow-ups','lucide:message-square-more',
    'cloudmold/crm/followup/index','CloudMoldCrmFollowUp'
  UNION ALL SELECT 9100000000806,'商机','cloudmold:crm:query',7,'opportunities','lucide:badge-dollar-sign',
    'cloudmold/crm/business/index','CloudMoldCrmOpportunity'
  UNION ALL SELECT 9100000000807,'销售合同','cloudmold:crm:sales-contract:query',8,'sales-contracts','lucide:file-signature',
    'cloudmold/crm/sales-contract/index','CloudMoldCrmSalesContract'
) c LEFT JOIN system_menu existing ON existing.id=c.id WHERE existing.id IS NULL;

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,
   always_show,creator,create_time,updater,update_time,deleted)
SELECT c.id,c.name,c.permission,3,c.sort,c.parent_id,'','','',NULL,0,b'1',b'1',b'1','CloudMold',
       UTC_TIMESTAMP(6),'CloudMold:crm-navigation',UTC_TIMESTAMP(6),b'0'
FROM (
  SELECT 9100000000850 id,'CRM 查询' name,'cloudmold:crm:query' permission,1 sort,9100000000800 parent_id
  UNION ALL SELECT 9100000000851,'CRM 操作','cloudmold:crm:command',2,9100000000800
  UNION ALL SELECT 9100000000852,'销售合同查询','cloudmold:crm:sales-contract:query',1,9100000000807
  UNION ALL SELECT 9100000000853,'销售合同操作','cloudmold:crm:sales-contract:command',2,9100000000807
  UNION ALL SELECT 9100000000854,'应收摘要查询','cloudmold:finance:receivables:query',3,9100000000807
  UNION ALL SELECT 9100000000855,'应收操作','cloudmold:finance:receivables:command',4,9100000000807
) c LEFT JOIN system_menu existing ON existing.id=c.id WHERE existing.id IS NULL;

-- The business role is separate from tenant administration. It can be assigned
-- to sales operators without granting unrelated CloudMold administration.
INSERT INTO system_role
  (name,code,sort,data_scope,data_scope_dept_ids,status,type,remark,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT '客户与销售','customer_sales_operator',30,4,'',0,2,
       'CloudMold 客户、线索、联系人、跟进、商机、销售合同与应收协同岗位',
       'CloudMold:crm-navigation',UTC_TIMESTAMP(6),'CloudMold:crm-navigation',UTC_TIMESTAMP(6),b'0',r.tenant_id
FROM system_role r
WHERE r.code='tenant_admin' AND r.status=0 AND r.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1 FROM system_role existing
    WHERE existing.tenant_id=r.tenant_id AND existing.code='customer_sales_operator'
      AND existing.deleted=b'0'
  );

INSERT INTO system_role_menu (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT r.id,m.id,r.tenant_id,'CloudMold',UTC_TIMESTAMP(6),'CloudMold:crm-navigation',UTC_TIMESTAMP(6),b'0'
FROM system_role r JOIN system_menu m ON m.id IN (9100000000180,9100000000800,9100000000801,
  9100000000802,9100000000803,9100000000804,9100000000805,9100000000806,9100000000807,
  9100000000850,9100000000851,9100000000852,9100000000853,9100000000854,
  9100000000855) AND m.deleted=b'0'
LEFT JOIN system_role_menu rm ON rm.role_id=r.id AND rm.menu_id=m.id AND rm.tenant_id=r.tenant_id AND rm.deleted=b'0'
WHERE r.code IN ('super_admin','tenant_admin','customer_sales_operator')
  AND r.deleted=b'0' AND rm.menu_id IS NULL;
