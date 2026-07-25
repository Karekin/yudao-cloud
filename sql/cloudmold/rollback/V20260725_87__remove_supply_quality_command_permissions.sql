-- Recoverable rollback for V20260725_87.

DELETE FROM system_role_menu
WHERE menu_id IN (9100000000440, 9100000000441, 9100000000442, 9100000000443);

DELETE FROM system_menu
WHERE id IN (9100000000440, 9100000000441, 9100000000442, 9100000000443)
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:supply-quality-command';
