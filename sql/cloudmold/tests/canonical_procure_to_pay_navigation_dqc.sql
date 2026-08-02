-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

WITH expected AS (
  SELECT 9100000000460 id,'采购工作台' name,'' permission,2 type,5 sort,9100000000140 parent_id,'procurement' path,'cloudmold/procurement/index' component,'CloudMoldProcurement' component_name
  UNION ALL SELECT 9100000000461,'采购入库','cloudmold:warehouse:query',2,6,9100000000140,'procurement-inbound','cloudmold/procurement-inbound/index','CloudMoldProcurementInbound'
  UNION ALL SELECT 9100000000462,'来货质检','cloudmold:quality:procurement-receipt-inspection:query',2,2,9100000000150,'procurement-quality','cloudmold/procurement-quality/index','CloudMoldProcurementQuality'
  UNION ALL SELECT 9100000000463,'采购到付款','cloudmold:finance:procure-to-pay:query',2,3,9100000000170,'procure-to-pay','cloudmold/finance/index','CloudMoldProcureToPay'
)
SELECT expected.id AS missing_or_drifted_page_id
FROM expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL
   OR actual.name<>expected.name
   OR actual.permission<>expected.permission
   OR actual.type<>expected.type
   OR actual.sort<>expected.sort
   OR actual.parent_id<>expected.parent_id
   OR actual.path<>expected.path
   OR actual.component<>expected.component
   OR actual.component_name<>expected.component_name
   OR actual.status<>0 OR actual.visible<>b'1' OR actual.deleted<>b'0';

WITH expected AS (
  SELECT 9100000000464 id,9100000000460 parent_id,'cloudmold:procurement:requisition:query' permission
  UNION ALL SELECT 9100000000465,9100000000460,'cloudmold:procurement:sourcing:query'
  UNION ALL SELECT 9100000000466,9100000000460,'cloudmold:procurement:quotation:query'
  UNION ALL SELECT 9100000000467,9100000000460,'cloudmold:procurement:award:query'
  UNION ALL SELECT 9100000000468,9100000000460,'cloudmold:procurement:order:query'
  UNION ALL SELECT 9100000000469,9100000000460,'cloudmold:procurement:order:write'
  UNION ALL SELECT 9100000000470,9100000000460,'cloudmold:procurement:order:release'
  UNION ALL SELECT 9100000000471,9100000000460,'cloudmold:procurement:sourcing:write'
  UNION ALL SELECT 9100000000472,9100000000460,'cloudmold:procurement:quotation:write'
  UNION ALL SELECT 9100000000473,9100000000460,'cloudmold:procurement:evaluation:write'
  UNION ALL SELECT 9100000000474,9100000000460,'cloudmold:procurement:award:write'
  UNION ALL SELECT 9100000000475,9100000000460,'cloudmold:procurement:award:submit'
  UNION ALL SELECT 9100000000476,9100000000460,'cloudmold:procurement:award:approve'
  UNION ALL SELECT 9100000000477,9100000000461,'cloudmold:warehouse:query'
  UNION ALL SELECT 9100000000478,9100000000461,'cloudmold:warehouse:command'
  UNION ALL SELECT 9100000000479,9100000000462,'cloudmold:quality:procurement-receipt-inspection:query'
  UNION ALL SELECT 9100000000480,9100000000462,'cloudmold:quality:procurement-receipt-inspection:command'
  UNION ALL SELECT 9100000000481,9100000000463,'cloudmold:finance:procure-to-pay:query'
  UNION ALL SELECT 9100000000482,9100000000463,'cloudmold:finance:procure-to-pay:command'
  UNION ALL SELECT 9100000000483,9100000000463,'cloudmold:finance:procure-to-pay:govern'
)
SELECT expected.id AS missing_or_drifted_permission_id
FROM expected
LEFT JOIN system_menu actual ON actual.id=expected.id
WHERE actual.id IS NULL
   OR actual.parent_id<>expected.parent_id
   OR actual.permission<>expected.permission
   OR actual.type<>3
   OR actual.path<>''
   OR actual.component IS NOT NULL
   OR actual.status<>0 OR actual.deleted<>b'0';

SELECT id,name,parent_id,path,component
FROM system_menu
WHERE id BETWEEN 9100000000460 AND 9100000000483
  AND deleted=b'0'
  AND (component LIKE 'erp/%'
    OR component LIKE 'wms/%'
    OR path='erp'
    OR path='wms');

SELECT id,name,parent_id,path,component
FROM system_menu
WHERE deleted=b'0'
  AND id NOT BETWEEN 9100000000460 AND 9100000000483
  AND (
    (parent_id=9100000000140 AND path IN ('procurement','procurement-inbound'))
    OR (parent_id=9100000000150 AND path='procurement-quality')
    OR (parent_id=9100000000170 AND path='procure-to-pay')
    OR component IN (
      'cloudmold/procurement/index',
      'cloudmold/procurement-inbound/index',
      'cloudmold/procurement-quality/index',
      'cloudmold/finance/index'
    )
  );

SELECT 'canonical_menu_count' AS check_name,COUNT(*) AS actual_count
FROM system_menu
WHERE id BETWEEN 9100000000460 AND 9100000000483
  AND deleted=b'0'
HAVING actual_count<>24;
