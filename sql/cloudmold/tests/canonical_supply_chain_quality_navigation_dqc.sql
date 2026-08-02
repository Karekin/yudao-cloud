-- V159 incoming-quality navigation quality gates.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'incoming_quality_under_procurement_execution' AS check_name,
       COUNT(*) AS violation_count
FROM system_menu menu_row
WHERE menu_row.id = 9100000000462
  AND (
    menu_row.parent_id <> 9100000000491
    OR menu_row.path <> 'procurement-quality'
    OR menu_row.permission <> 'cloudmold:quality:procurement-receipt-inspection:query'
    OR menu_row.sort <> 5
    OR menu_row.status <> 0
    OR menu_row.visible <> b'1'
    OR menu_row.deleted <> b'0'
  );

SELECT 'incoming_quality_roles_missing_supply_chain_parent' AS check_name,
       COUNT(*) AS violation_count
FROM system_role_menu child_grant
JOIN system_role role
  ON role.id = child_grant.role_id
 AND role.tenant_id = child_grant.tenant_id
 AND role.deleted = b'0'
 AND role.status = 0
WHERE child_grant.menu_id = 9100000000462
  AND child_grant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu parent_grant
    WHERE parent_grant.role_id = child_grant.role_id
      AND parent_grant.tenant_id = child_grant.tenant_id
      AND parent_grant.menu_id = 9100000000491
      AND parent_grant.deleted = b'0'
  );
