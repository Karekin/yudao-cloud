-- Least-privilege permissions for the new supply-planning and quality command surfaces.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT candidate.id, candidate.name, candidate.permission, 3, candidate.sort,
       candidate.parent_id, '', '', '', NULL, 0, b'1', b'1', b'1',
       'CloudMold', UTC_TIMESTAMP(6), 'CloudMold:supply-quality-command',
       UTC_TIMESTAMP(6), b'0'
FROM (
    SELECT 9100000000440 AS id, '供应链计划查询' AS name,
           'cloudmold:supply-planning:query' AS permission, 3 AS sort,
           9100000000320 AS parent_id
    UNION ALL SELECT 9100000000441, '供应链计划操作',
           'cloudmold:supply-planning:command', 4, 9100000000320
    UNION ALL SELECT 9100000000442, '鉴别质检查询',
           'cloudmold:quality:query', 3, 9100000000330
    UNION ALL SELECT 9100000000443, '鉴别质检操作',
           'cloudmold:quality:command', 4, 9100000000330
) candidate
LEFT JOIN system_menu existing ON existing.id = candidate.id
WHERE existing.id IS NULL;
