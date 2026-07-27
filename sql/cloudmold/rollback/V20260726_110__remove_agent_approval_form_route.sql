SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DELETE FROM system_role_menu
WHERE menu_id = 9100000000074;

UPDATE system_tenant_package package
JOIN (
    SELECT source.id, JSON_ARRAYAGG(items.menu_id ORDER BY items.ordinality) AS menu_ids
    FROM system_tenant_package source
    JOIN JSON_TABLE(
        CAST(source.menu_ids AS JSON),
        '$[*]' COLUMNS (
            ordinality FOR ORDINALITY,
            menu_id BIGINT PATH '$'
        )
    ) items
    WHERE items.menu_id <> 9100000000074
    GROUP BY source.id
) filtered ON filtered.id = package.id
SET package.menu_ids = CAST(filtered.menu_ids AS CHAR CHARACTER SET utf8mb4),
    updater = 'CloudMold:rollback-agent-approval-form-route',
    update_time = UTC_TIMESTAMP(6)
WHERE package.deleted = b'0'
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000074 AS JSON));

DELETE FROM system_menu
WHERE id = 9100000000074
  AND creator = 'CloudMold';
