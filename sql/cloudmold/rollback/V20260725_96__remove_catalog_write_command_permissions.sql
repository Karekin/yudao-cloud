DELETE FROM system_role_menu
WHERE menu_id IN (9100000000209, 9100000000210);

DELETE FROM system_menu
WHERE id IN (9100000000209, 9100000000210)
  AND updater = 'CloudMold:catalog-write-permissions';
