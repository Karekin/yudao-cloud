-- Publish the canonical procure-to-pay navigation to full CloudMold tenant packages.
--
-- V144/V153/V155 registered the page and permission nodes, but system_menu alone is
-- not sufficient in yudao's tenant RBAC model. A tenant can only receive menu nodes
-- present in its package, and an existing tenant administrator also needs explicit
-- system_role_menu rows. This migration performs that durable publication without
-- granting the new permissions to arbitrary business roles.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v157_menu_contract (
  menu_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO cloudmold_v157_menu_contract (menu_id)
VALUES
  (9100000000450),(9100000000451),
  (9100000000460),(9100000000461),(9100000000462),(9100000000463),
  (9100000000464),(9100000000465),(9100000000466),(9100000000467),
  (9100000000468),(9100000000469),(9100000000470),(9100000000471),
  (9100000000472),(9100000000473),(9100000000474),(9100000000475),
  (9100000000476),(9100000000477),(9100000000478),(9100000000479),
  (9100000000480),(9100000000481),(9100000000482),(9100000000483),
  (9100000000484);

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_contract_guard;
CREATE TEMPORARY TABLE cloudmold_v157_contract_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v157_contract_guard CHECK (guard_value = 'OK')
);

-- Refuse partial or stale navigation contracts. Authorization must never make a
-- missing or retired route reachable.
INSERT INTO cloudmold_v157_contract_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 27 THEN 'OK' ELSE 'FAIL' END
FROM cloudmold_v157_menu_contract expected
JOIN system_menu actual ON actual.id = expected.menu_id
WHERE actual.status = 0
  AND actual.deleted = b'0';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v157_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

-- The procure-to-pay bundle spans Supply Chain, Quality and Finance. Only an
-- active package already carrying the CloudMold root and all three governed
-- domain parents is a full CloudMold operating package eligible for this bundle.
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

DROP TEMPORARY TABLE IF EXISTS cloudmold_v157_package_menu;
CREATE TEMPORARY TABLE cloudmold_v157_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id, menu_id)
) ENGINE=InnoDB;

-- Normalize the eligible package menu arrays while preserving every existing ID.
INSERT IGNORE INTO cloudmold_v157_package_menu (package_id, menu_id)
SELECT eligible.package_id, existing.menu_id
FROM cloudmold_v157_eligible_package eligible
JOIN system_tenant_package package ON package.id = eligible.package_id
JOIN JSON_TABLE(
  CAST(package.menu_ids AS JSON),
  '$[*]' COLUMNS (menu_id BIGINT PATH '$')
) existing;

INSERT IGNORE INTO cloudmold_v157_package_menu (package_id, menu_id)
SELECT eligible.package_id, expected.menu_id
FROM cloudmold_v157_eligible_package eligible
CROSS JOIN cloudmold_v157_menu_contract expected;

UPDATE system_tenant_package package
JOIN (
  SELECT package_id, JSON_ARRAYAGG(menu_id) AS menu_ids
  FROM cloudmold_v157_package_menu
  GROUP BY package_id
) merged ON merged.package_id = package.id
SET package.menu_ids = CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater = 'CloudMold:canonical-procure-to-pay-tenant-authorization',
    package.update_time = UTC_TIMESTAMP(6);

-- Existing tenant administrators mirror their active tenant package. Business
-- roles remain least-privilege and must be granted through normal role governance.
-- Remove only mis-scoped rows created by this exact migration marker. Direct SQL
-- does not pass through Yudao's tenant interceptor, so tenant_id is mandatory.
DELETE mis_scoped
FROM system_role_menu mis_scoped
JOIN system_role role ON role.id = mis_scoped.role_id
JOIN system_tenant tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = b'0'
JOIN cloudmold_v157_eligible_package eligible
  ON eligible.package_id = tenant.package_id
JOIN cloudmold_v157_menu_contract expected
  ON expected.menu_id = mis_scoped.menu_id
WHERE mis_scoped.tenant_id <> role.tenant_id
  AND mis_scoped.creator = 'CloudMold'
  AND mis_scoped.updater = 'CloudMold:canonical-procure-to-pay-tenant-authorization';

INSERT INTO system_role_menu
  (role_id, menu_id, tenant_id, creator, create_time, updater, update_time, deleted)
SELECT role.id, expected.menu_id, role.tenant_id,
       'CloudMold', UTC_TIMESTAMP(6),
       'CloudMold:canonical-procure-to-pay-tenant-authorization', UTC_TIMESTAMP(6), b'0'
FROM system_role role
JOIN system_tenant tenant
  ON tenant.id = role.tenant_id
 AND tenant.deleted = b'0'
 AND tenant.status = 0
JOIN cloudmold_v157_eligible_package eligible
  ON eligible.package_id = tenant.package_id
CROSS JOIN cloudmold_v157_menu_contract expected
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

DROP TEMPORARY TABLE cloudmold_v157_package_menu;
DROP TEMPORARY TABLE cloudmold_v157_eligible_package;
DROP TEMPORARY TABLE cloudmold_v157_contract_guard;
DROP TEMPORARY TABLE cloudmold_v157_menu_contract;
