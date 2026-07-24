-- V82 startup preflight for CloudMold admin navigation (expected result: zero rows)

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 0) 备份快照必须完整。
SELECT 'backup_row_count' AS check_name,
       COUNT(*) AS actual_count
FROM cloudmold_admin_menu_ia_backup
WHERE migration_key = 'V20260724_82'
  AND menu_id IN (
    9100000000000, 9100000000001, 9100000000002,
    9100000000010, 9100000000011,
    9100000000020, 9100000000021, 9100000000022, 9100000000023,
    9100000000024, 9100000000025,
    9100000000030, 9100000000031,
    9100000000040, 9100000000041,
    9100000000042, 9100000000043,
    9100000000044, 9100000000050,
    9100000000051, 9100000000060,
    9100000000061, 9100000000070,
    9100000000071, 9100000000072,
    9100000000073, 9100000000080,
    9100000000081
  )
HAVING actual_count <> 28;

-- 1) Required root center menus must exist, visible, active under CloudMold root.
SELECT 'business_centers_missing' AS check_name,
       9100000000100 AS menu_id
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id=9100000000100 AND parent_id=9100000000000
    AND deleted=b'0' AND type=1 AND status=0 AND visible=b'1'
)
UNION ALL
SELECT 'business_centers_missing', 9100000000110
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id=9100000000110 AND parent_id=9100000000000
    AND deleted=b'0' AND type=1 AND status=0 AND visible=b'1'
)
UNION ALL
SELECT 'business_centers_missing', 9100000000120
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id=9100000000120 AND parent_id=9100000000000
    AND deleted=b'0' AND type=1 AND status=0 AND visible=b'1'
)
UNION ALL
SELECT 'business_centers_missing', 9100000000130
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id=9100000000130 AND parent_id=9100000000000
    AND deleted=b'0' AND type=1 AND status=0 AND visible=b'1'
)
UNION ALL
SELECT 'business_centers_missing', 9100000000140
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu WHERE id=9100000000140 AND parent_id=9100000000000
    AND deleted=b'0' AND type=1 AND status=0 AND visible=b'1'
);

-- 2) legacy aggregate must not leak as visible root leaf.
SELECT id, name, type, visible
FROM system_menu
WHERE deleted=b'0'
  AND parent_id = 9100000000000
  AND type = 2
  AND visible = b'1'
  AND id IN (9100000000020, 9100000000001, 9100000000040, 9100000000050, 9100000000080, 9100000000010, 9100000000060, 9100000000041, 9100000000042, 9100000000043, 9100000000044, 9100000000030);

-- 3) Default-off Agent Control must remain unavailable.
SELECT 'agent_control_visibility_violation' AS check_name, id, name, status, visible
FROM system_menu
WHERE id IN (9100000000070, 9100000000071, 9100000000072, 9100000000073)
  AND deleted=b'0'
  AND (status <> b'1' OR visible <> b'0');
