-- Retire legacy yudao domain navigation after CloudMold replacement pages are available.
-- Source modules, descendant menus, and role bindings stay intact for upstream mergeability.

CREATE TABLE IF NOT EXISTS cloudmold_legacy_menu_retirement (
    menu_id bigint NOT NULL,
    menu_name varchar(50) NOT NULL,
    expected_parent_id bigint NOT NULL,
    expected_path varchar(200) NOT NULL,
    original_status tinyint NOT NULL,
    original_visible bit(1) NOT NULL,
    retirement_marker varchar(64) NOT NULL,
    retired_at datetime(6) NOT NULL,
    restored_at datetime(6) DEFAULT NULL,
    PRIMARY KEY (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Reversible preimage for retired legacy domain menu roots';

INSERT IGNORE INTO cloudmold_legacy_menu_retirement
    (menu_id, menu_name, expected_parent_id, expected_path, original_status, original_visible,
     retirement_marker, retired_at, restored_at)
SELECT id, name, parent_id, path, status, visible,
       'CloudMold:legacy-domain-retirement', UTC_TIMESTAMP(6), NULL
FROM system_menu
WHERE deleted = b'0'
  AND (
       (id = 2000 AND parent_id = 2362 AND path = 'product')
    OR (id = 2030 AND parent_id = 2362 AND path = 'promotion')
    OR (id = 2072 AND parent_id = 2362 AND path = 'trade')
    OR (id = 2563 AND parent_id = 0 AND path = '/erp')
    OR (id = 6400 AND parent_id = 0 AND path = '/wms')
  );

UPDATE system_menu m
JOIN cloudmold_legacy_menu_retirement r ON r.menu_id = m.id
SET m.status = 1,
    m.updater = r.retirement_marker,
    m.update_time = UTC_TIMESTAMP(6)
WHERE r.restored_at IS NULL
  AND m.deleted = b'0'
  AND m.parent_id = r.expected_parent_id
  AND m.path = r.expected_path;
