-- Grant the reconciliation permission nodes to full CloudMold tenant packages
-- and existing tenant administrators.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v173_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v173_menu_contract (
  menu_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v173_menu_contract (menu_id)
VALUES (9100000000522),(9100000000485),(9100000000486);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v173_contract_guard;
CREATE TEMPORARY TABLE cloudmold_v173_contract_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v173_contract_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v173_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 3 THEN 'OK' ELSE 'FAIL' END
FROM cloudmold_v173_menu_contract expected
JOIN system_menu actual ON actual.id = expected.menu_id
WHERE actual.status = 0
  AND actual.deleted = b'0';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v173_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v173_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v173_eligible_package (package_id)
SELECT package.id
FROM system_tenant_package package
WHERE package.deleted = b'0'
  AND package.status = 0
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000463 AS JSON));

DROP TEMPORARY TABLE IF EXISTS cloudmold_v173_package_menu;
CREATE TEMPORARY TABLE cloudmold_v173_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id, menu_id)
) ENGINE=InnoDB;

INSERT IGNORE INTO cloudmold_v173_package_menu (package_id, menu_id)
SELECT eligible.package_id, existing.menu_id
FROM cloudmold_v173_eligible_package eligible
JOIN system_tenant_package package ON package.id = eligible.package_id
JOIN JSON_TABLE(
  CAST(package.menu_ids AS JSON),
  '$[*]' COLUMNS (menu_id BIGINT PATH '$')
) existing;

INSERT IGNORE INTO cloudmold_v173_package_menu (package_id, menu_id)
SELECT eligible.package_id, expected.menu_id
FROM cloudmold_v173_eligible_package eligible
CROSS JOIN cloudmold_v173_menu_contract expected;

UPDATE system_tenant_package package
JOIN (
  SELECT package_id, JSON_ARRAYAGG(menu_id) AS menu_ids
  FROM cloudmold_v173_package_menu
  GROUP BY package_id
) merged ON merged.package_id = package.id
SET package.menu_ids = CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater = 'CloudMold:finance-reconciliation-auth-v173',
    package.update_time = UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT role.id, expected.menu_id, role.tenant_id,
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:finance-reconciliation-auth-v173', UTC_TIMESTAMP(6), b'0'
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = b'0'
 AND tenant.status = 0
JOIN cloudmold_v173_eligible_package eligible
  ON eligible.package_id = tenant.package_id
CROSS JOIN cloudmold_v173_menu_contract expected
WHERE role.code = 'tenant_admin'
  AND role.deleted = b'0'
  AND role.status = 0
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu existing
    WHERE existing.role_id = role.id
      AND existing.menu_id = expected.menu_id
      AND existing.tenant_id = role.tenant_id
      AND existing.deleted = b'0'
  );

DROP TEMPORARY TABLE cloudmold_v173_package_menu;
DROP TEMPORARY TABLE cloudmold_v173_eligible_package;
DROP TEMPORARY TABLE cloudmold_v173_contract_guard;
DROP TEMPORARY TABLE cloudmold_v173_menu_contract;
