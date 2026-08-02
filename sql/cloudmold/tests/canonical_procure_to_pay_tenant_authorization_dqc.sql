-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_expected_menu;
CREATE TEMPORARY TABLE cloudmold_v157_expected_menu (
  menu_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v157_expected_menu (menu_id)
VALUES
  (9100000000450),(9100000000451),
  (9100000000460),(9100000000461),(9100000000462),(9100000000463),
  (9100000000464),(9100000000465),(9100000000466),(9100000000467),
  (9100000000468),(9100000000469),(9100000000470),(9100000000471),
  (9100000000472),(9100000000473),(9100000000474),(9100000000475),
  (9100000000476),(9100000000477),(9100000000478),(9100000000479),
  (9100000000480),(9100000000481),(9100000000482),(9100000000483),
  (9100000000484);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v157_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v157_eligible_package (package_id)
SELECT package.id
FROM system_tenant_package package
WHERE package.deleted = b'0'
  AND package.status = 0
  AND JSON_VALID(package.menu_ids)
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000000 AS JSON))
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000140 AS JSON))
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000150 AS JSON))
  AND JSON_CONTAINS(CAST(package.menu_ids AS JSON), CAST(9100000000170 AS JSON));

-- Every full CloudMold operating package must contain the complete canonical
-- procure-to-pay navigation and permission bundle.
SELECT eligible.package_id, expected.menu_id AS missing_package_menu_id
FROM cloudmold_v157_eligible_package eligible
CROSS JOIN cloudmold_v157_expected_menu expected
JOIN system_tenant_package package ON package.id = eligible.package_id
WHERE NOT JSON_CONTAINS(
  CAST(package.menu_ids AS JSON),
  CAST(expected.menu_id AS JSON)
);

-- Every active tenant administrator must receive exactly the menu IDs allowed by
-- the tenant's active package. This checks the newly published bundle only.
SELECT role.id AS role_id, expected.menu_id AS missing_role_menu_id
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = b'0'
 AND tenant.status = 0
JOIN cloudmold_v157_eligible_package eligible
  ON eligible.package_id = tenant.package_id
CROSS JOIN cloudmold_v157_expected_menu expected
WHERE role.code = 'tenant_admin'
  AND role.deleted = b'0'
  AND role.status = 0
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu role_menu
    WHERE role_menu.role_id = role.id
      AND role_menu.menu_id = expected.menu_id
      AND role_menu.tenant_id = role.tenant_id
      AND role_menu.deleted = b'0'
  );

-- No active tenant administrator may hold a new menu outside its package.
SELECT role.id AS role_id, role_menu.menu_id AS out_of_package_menu_id
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = b'0'
JOIN system_tenant_package package
  ON package.id = tenant.package_id
 AND package.deleted = b'0'
JOIN system_role_menu role_menu
  ON role_menu.role_id = role.id
 AND role_menu.tenant_id = role.tenant_id
 AND role_menu.deleted = b'0'
JOIN cloudmold_v157_expected_menu expected ON expected.menu_id = role_menu.menu_id
WHERE role.code = 'tenant_admin'
  AND role.deleted = b'0'
  AND NOT JSON_CONTAINS(
    CAST(package.menu_ids AS JSON),
    CAST(role_menu.menu_id AS JSON)
  );

-- Direct SQL must never leave this migration's grants in tenant 0 or another
-- tenant. Such rows look present in raw counts but are invisible to the app.
SELECT role_menu.role_id, role_menu.menu_id, role_menu.tenant_id AS mis_scoped_tenant_id
FROM system_role_menu role_menu
JOIN system_role role ON role.id = role_menu.role_id
JOIN cloudmold_v157_expected_menu expected ON expected.menu_id = role_menu.menu_id
WHERE role_menu.creator = 'CloudMold'
  AND role_menu.updater = 'CloudMold:canonical-procure-to-pay-tenant-authorization'
  AND role_menu.tenant_id <> role.tenant_id;

DROP TEMPORARY TABLE cloudmold_v157_eligible_package;
DROP TEMPORARY TABLE cloudmold_v157_expected_menu;
