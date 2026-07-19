-- CloudMold-owned Data Readiness navigation. External evidence remains UNKNOWN until an observer
-- supplies timestamped CDC, DQC, ADS, and source-graduation evidence.

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000030, '数据就绪度', 'cloudmold:data-readiness:query', 2, 4, 9100000000000,
       'data-readiness', 'lucide:activity', 'cloudmold/data-readiness/index', 'CloudMoldDataReadiness',
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000030);

INSERT INTO system_menu
    (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
     status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9100000000031, '数据就绪度查询', 'cloudmold:data-readiness:query', 3, 1, 9100000000030,
       '', '', '', NULL,
       0, b'1', b'1', b'1', 'CloudMold', UTC_TIMESTAMP(6), 'CloudMold', UTC_TIMESTAMP(6), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 9100000000031);
