-- Manual rollback for V20260724_82.
-- Restore only rows captured before the information-architecture migration.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu m
JOIN cloudmold_admin_menu_ia_backup b
  ON b.menu_id = m.id
 AND b.migration_key = 'V20260724_82'
SET m.name = b.name,
    m.permission = b.permission,
    m.type = b.type,
    m.sort = b.sort,
    m.parent_id = b.parent_id,
    m.path = b.path,
    m.icon = b.icon,
    m.component = b.component,
    m.component_name = b.component_name,
    m.status = b.status,
    m.visible = b.visible,
    m.keep_alive = b.keep_alive,
    m.always_show = b.always_show,
    m.updater = 'CloudMold:business-navigation-rollback',
    m.update_time = UTC_TIMESTAMP(6)
WHERE m.deleted = b'0';

DELETE FROM system_menu
WHERE id IN (
    9100000000100,
    9100000000110,
    9100000000120,
    9100000000130,
    9100000000140
)
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:business-navigation'
  AND deleted = b'0';
