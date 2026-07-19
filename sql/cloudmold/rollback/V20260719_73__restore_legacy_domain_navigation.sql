-- Manual rollback for V20260719_73. Restore only roots still owned by the retirement marker;
-- do not overwrite later operator or upstream changes.

UPDATE system_menu m
JOIN cloudmold_legacy_menu_retirement r ON r.menu_id = m.id
SET m.status = r.original_status,
    m.visible = r.original_visible,
    m.updater = 'CloudMold:legacy-domain-restore',
    m.update_time = UTC_TIMESTAMP(6)
WHERE r.restored_at IS NULL
  AND m.updater COLLATE utf8mb4_unicode_ci = r.retirement_marker COLLATE utf8mb4_unicode_ci
  AND m.parent_id = r.expected_parent_id
  AND m.path COLLATE utf8mb4_unicode_ci = r.expected_path COLLATE utf8mb4_unicode_ci;

UPDATE cloudmold_legacy_menu_retirement r
JOIN system_menu m ON m.id = r.menu_id
SET r.restored_at = UTC_TIMESTAMP(6)
WHERE r.restored_at IS NULL
  AND m.updater = 'CloudMold:legacy-domain-restore';
