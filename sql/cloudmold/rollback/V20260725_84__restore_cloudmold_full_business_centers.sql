-- Manual rollback for V20260725_84.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

UPDATE system_menu menu
JOIN cloudmold_admin_menu_ia_backup backup
  ON backup.menu_id = menu.id
 AND backup.migration_key = 'V20260725_84'
SET menu.name = backup.name,
    menu.permission = backup.permission,
    menu.type = backup.type,
    menu.sort = backup.sort,
    menu.parent_id = backup.parent_id,
    menu.path = backup.path,
    menu.icon = backup.icon,
    menu.component = backup.component,
    menu.component_name = backup.component_name,
    menu.status = backup.status,
    menu.visible = backup.visible,
    menu.keep_alive = backup.keep_alive,
    menu.always_show = backup.always_show,
    menu.updater = 'CloudMold:full-business-centers-rollback',
    menu.update_time = UTC_TIMESTAMP(6)
WHERE menu.deleted = b'0';

DELETE menu
FROM system_menu menu
LEFT JOIN cloudmold_admin_menu_ia_backup backup
  ON backup.menu_id = menu.id
 AND backup.migration_key = 'V20260725_84'
WHERE menu.id BETWEEN 9100000000000 AND 9100000000999
  AND menu.creator = 'CloudMold'
  AND menu.updater = 'CloudMold:full-business-centers'
  AND menu.deleted = b'0'
  AND backup.menu_id IS NULL;
