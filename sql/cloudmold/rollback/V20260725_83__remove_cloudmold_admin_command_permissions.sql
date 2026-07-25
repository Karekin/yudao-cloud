-- Recoverable rollback for V20260725_83.
-- Role bindings must be removed before their menu records.

DELETE FROM system_role_menu
WHERE menu_id BETWEEN 9100000000200 AND 9100000000208;

DELETE FROM system_menu
WHERE id BETWEEN 9100000000200 AND 9100000000208
  AND creator = 'CloudMold'
  AND updater = 'CloudMold:command-permissions';
