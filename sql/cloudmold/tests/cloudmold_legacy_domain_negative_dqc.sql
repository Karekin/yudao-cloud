-- Expected result: every query returns zero rows.
-- This is the database layer of the four-layer legacy-domain negative proof.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- The five retired roots must retain an exact reversible preimage.
SELECT expected.menu_id, 'missing_retirement_preimage' AS violation
FROM (
    SELECT 2000 AS menu_id
    UNION ALL SELECT 2030
    UNION ALL SELECT 2072
    UNION ALL SELECT 2563
    UNION ALL SELECT 6400
) expected
LEFT JOIN cloudmold_legacy_menu_retirement retirement
  ON retirement.menu_id = expected.menu_id
 AND retirement.retirement_marker = 'CloudMold:legacy-domain-retirement'
 AND retirement.restored_at IS NULL
WHERE retirement.menu_id IS NULL;

SELECT COUNT(*) AS unresolved_retirement_count,
       'unexpected_retirement_scope' AS violation
FROM cloudmold_legacy_menu_retirement
WHERE retirement_marker = 'CloudMold:legacy-domain-retirement'
  AND restored_at IS NULL
HAVING COUNT(*) <> 5;

-- Product, Promotion, Trade, ERP and WMS roots must stay disabled while their
-- source definitions and descendant role bindings remain available for upstream merges.
SELECT expected.menu_id, menu.name, menu.path, menu.status, menu.updater,
       'legacy_root_not_disabled_reversibly' AS violation
FROM (
    SELECT 2000 AS menu_id, 2362 AS expected_parent_id, 'product' AS expected_path
    UNION ALL SELECT 2030, 2362, 'promotion'
    UNION ALL SELECT 2072, 2362, 'trade'
    UNION ALL SELECT 2563, 0, '/erp'
    UNION ALL SELECT 6400, 0, '/wms'
) expected
LEFT JOIN system_menu menu
  ON menu.id = expected.menu_id
LEFT JOIN cloudmold_legacy_menu_retirement retirement
  ON retirement.menu_id = expected.menu_id
 AND retirement.retirement_marker = 'CloudMold:legacy-domain-retirement'
 AND retirement.restored_at IS NULL
WHERE menu.id IS NULL
   OR retirement.menu_id IS NULL
   OR retirement.expected_parent_id <> expected.expected_parent_id
   OR retirement.expected_path COLLATE utf8mb4_unicode_ci
      <> expected.expected_path COLLATE utf8mb4_unicode_ci
   OR (
      menu.deleted <> b'0'
      OR menu.status <> 1
      OR menu.updater COLLATE utf8mb4_unicode_ci
         <> retirement.retirement_marker COLLATE utf8mb4_unicode_ci
      OR menu.parent_id <> retirement.expected_parent_id
      OR menu.path COLLATE utf8mb4_unicode_ci
         <> retirement.expected_path COLLATE utf8mb4_unicode_ci
  );

-- CloudMold business pages must never be nested below a retired legacy root.
SELECT canonical.id, canonical.name, canonical.parent_id,
       'canonical_page_nested_below_legacy_root' AS violation
FROM system_menu canonical
JOIN cloudmold_legacy_menu_retirement retirement
  ON retirement.menu_id = canonical.parent_id
WHERE canonical.deleted = b'0'
  AND canonical.id BETWEEN 9100000000000 AND 9100000000999;

-- The five canonical business groups are the positive replacement boundary.
SELECT expected.menu_id, menu.name, menu.path, menu.status,
       'canonical_business_group_unavailable' AS violation
FROM (
    SELECT 9100000000100 AS menu_id, 'product-center' AS expected_path
    UNION ALL SELECT 9100000000110, 'merchant-channel'
    UNION ALL SELECT 9100000000120, 'inventory-warehouse'
    UNION ALL SELECT 9100000000130, 'order-fulfillment'
    UNION ALL SELECT 9100000000140, 'data-operations'
) expected
LEFT JOIN system_menu menu
  ON menu.id = expected.menu_id
WHERE menu.id IS NULL
   OR menu.deleted <> b'0'
   OR menu.status <> 0
   OR menu.parent_id <> 9100000000000
   OR menu.path COLLATE utf8mb4_unicode_ci
      <> expected.expected_path COLLATE utf8mb4_unicode_ci;
