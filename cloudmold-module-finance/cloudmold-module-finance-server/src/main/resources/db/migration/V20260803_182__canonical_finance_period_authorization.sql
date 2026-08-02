-- Publish canonical finance-period governance permissions to CloudMold tenant administrators.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v182_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v182_menu_contract (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  sort INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cloudmold_v182_menu_contract (id,name,permission,sort) VALUES
  (9100000000550,'会计期间查询','cloudmold:finance-close:query',10),
  (9100000000551,'会计期间治理','cloudmold:finance-close:command',11);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v182_contract_guard;
CREATE TEMPORARY TABLE cloudmold_v182_contract_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v182_contract_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v182_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu existing
JOIN cloudmold_v182_menu_contract expected ON expected.id = existing.id
WHERE NOT (
  existing.name = expected.name
  AND existing.permission = expected.permission
  AND existing.type = 3
  AND existing.sort = expected.sort
  AND existing.parent_id = 9100000000463
  AND existing.status = 0
  AND existing.deleted = b'0'
);

INSERT INTO cloudmold_v182_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu existing
JOIN cloudmold_v182_menu_contract expected
  ON expected.permission = existing.permission AND expected.id <> existing.id
WHERE existing.deleted = b'0';

INSERT INTO cloudmold_v182_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'FAIL' END
FROM system_menu parent
WHERE parent.id = 9100000000463
  AND parent.type = 2
  AND parent.status = 0
  AND parent.deleted = b'0';

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,3,expected.sort,9100000000463,
       '','',NULL,NULL,0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-finance-period-authorization',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v182_menu_contract expected
LEFT JOIN system_menu existing ON existing.id = expected.id
WHERE existing.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v182_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v182_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v182_eligible_package (package_id)
SELECT package.id
FROM system_tenant_package package
WHERE package.deleted = b'0'
  AND package.status = 0
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000463 AS JSON));

DROP TEMPORARY TABLE IF EXISTS cloudmold_v182_package_menu;
CREATE TEMPORARY TABLE cloudmold_v182_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id,menu_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO cloudmold_v182_package_menu (package_id,menu_id)
SELECT eligible.package_id, existing.menu_id
FROM cloudmold_v182_eligible_package eligible
JOIN system_tenant_package package ON package.id = eligible.package_id
JOIN JSON_TABLE(CAST(package.menu_ids AS JSON), '$[*]' COLUMNS (menu_id BIGINT PATH '$')) existing;

INSERT IGNORE INTO cloudmold_v182_package_menu (package_id,menu_id)
SELECT eligible.package_id, expected.id
FROM cloudmold_v182_eligible_package eligible
CROSS JOIN cloudmold_v182_menu_contract expected;

UPDATE system_tenant_package package
JOIN (
  SELECT package_id,JSON_ARRAYAGG(menu_id) AS menu_ids
  FROM cloudmold_v182_package_menu GROUP BY package_id
) merged ON merged.package_id = package.id
SET package.menu_ids = CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater = 'CloudMold:canonical-finance-period-authorization',
    package.update_time = UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT role.id,expected.id,role.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-finance-period-authorization',UTC_TIMESTAMP(6),b'0'
FROM system_role role
JOIN system_tenant tenant ON tenant.id = role.tenant_id
  AND tenant.deleted = b'0' AND tenant.status = 0
JOIN cloudmold_v182_eligible_package eligible ON eligible.package_id = tenant.package_id
CROSS JOIN cloudmold_v182_menu_contract expected
WHERE role.code = 'tenant_admin'
  AND role.deleted = b'0'
  AND role.status = 0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu current
    WHERE current.role_id = role.id
      AND current.menu_id = expected.id
      AND current.tenant_id = role.tenant_id
      AND current.deleted = b'0'
  );

DROP TEMPORARY TABLE cloudmold_v182_package_menu;
DROP TEMPORARY TABLE cloudmold_v182_eligible_package;
DROP TEMPORARY TABLE cloudmold_v182_contract_guard;
DROP TEMPORARY TABLE cloudmold_v182_menu_contract;

