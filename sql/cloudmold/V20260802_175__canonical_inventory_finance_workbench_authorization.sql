-- Grant each new workbench only to packages that already contain its owning
-- canonical CloudMold domain, then grant the same atomic nodes to tenant_admin.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v175_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v175_menu_contract (
  menu_id BIGINT NOT NULL PRIMARY KEY,
  required_parent_id BIGINT NOT NULL
) ENGINE=InnoDB;

INSERT INTO cloudmold_v175_menu_contract (menu_id,required_parent_id)
VALUES
  (9100000000523,9100000000493),(9100000000524,9100000000493),(9100000000525,9100000000493),
  (9100000000526,9100000000170),(9100000000527,9100000000170),(9100000000528,9100000000170);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v175_guard;
CREATE TEMPORARY TABLE cloudmold_v175_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v175_guard CHECK (guard_value='OK')
);

INSERT INTO cloudmold_v175_guard (guard_value)
SELECT CASE WHEN COUNT(*)=6 THEN 'OK' ELSE 'FAIL' END
FROM cloudmold_v175_menu_contract expected
JOIN system_menu actual ON actual.id=expected.menu_id
WHERE actual.status=0 AND actual.deleted=b'0';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v175_package_menu;
CREATE TEMPORARY TABLE cloudmold_v175_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id,menu_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO cloudmold_v175_package_menu (package_id,menu_id)
SELECT package.id,existing.menu_id
FROM system_tenant_package package
JOIN JSON_TABLE(CAST(package.menu_ids AS JSON),'$[*]' COLUMNS(menu_id BIGINT PATH '$')) existing
WHERE package.deleted=b'0' AND package.status=0 AND JSON_VALID(package.menu_ids);

INSERT IGNORE INTO cloudmold_v175_package_menu (package_id,menu_id)
SELECT package.id,expected.menu_id
FROM system_tenant_package package
JOIN cloudmold_v175_menu_contract expected
  ON JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.required_parent_id AS JSON))
WHERE package.deleted=b'0' AND package.status=0 AND JSON_VALID(package.menu_ids);

UPDATE system_tenant_package package
JOIN (
  SELECT package_id,JSON_ARRAYAGG(menu_id) menu_ids
  FROM cloudmold_v175_package_menu GROUP BY package_id
) merged ON merged.package_id=package.id
SET package.menu_ids=CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater='CloudMold:inventory-finance-auth-v175',
    package.update_time=UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT role.id,expected.menu_id,role.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:inventory-finance-auth-v175',UTC_TIMESTAMP(6),b'0'
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id=role.tenant_id AND tenant.deleted=b'0' AND tenant.status=0
JOIN system_tenant_package package
  ON package.id=tenant.package_id AND package.deleted=b'0' AND package.status=0
JOIN cloudmold_v175_menu_contract expected
  ON JSON_CONTAINS(CAST(package.menu_ids AS JSON),CAST(expected.menu_id AS JSON))
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu current
    WHERE current.role_id=role.id AND current.menu_id=expected.menu_id
      AND current.tenant_id=role.tenant_id AND current.deleted=b'0'
  );

DROP TEMPORARY TABLE cloudmold_v175_package_menu;
DROP TEMPORARY TABLE cloudmold_v175_guard;
DROP TEMPORARY TABLE cloudmold_v175_menu_contract;
