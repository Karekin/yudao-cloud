-- Move the already authoritative incoming-quality workspace into the
-- canonical supply-chain procurement execution hierarchy.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v159_guard;
CREATE TEMPORARY TABLE cloudmold_v159_guard (
  guard_value VARCHAR(5) NOT NULL,
  CONSTRAINT ck_cloudmold_v159_guard CHECK (guard_value = 'OK')
);

INSERT INTO cloudmold_v159_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 2 THEN 'OK' ELSE 'FAIL' END
FROM system_menu parent
WHERE parent.id IN (9100000000491,9100000000462)
  AND parent.status = 0
  AND parent.deleted = b'0';

INSERT INTO cloudmold_v159_guard (guard_value)
SELECT CASE WHEN COUNT(*) = 0 THEN 'OK' ELSE 'FAIL' END
FROM system_menu duplicate_page
WHERE duplicate_page.deleted = b'0'
  AND duplicate_page.parent_id = 9100000000491
  AND duplicate_page.path = 'procurement-quality'
  AND duplicate_page.id <> 9100000000462;

UPDATE system_menu
SET parent_id = 9100000000491,
    sort = 5,
    updater = 'CloudMold:canonical-supply-chain-quality-navigation',
    update_time = UTC_TIMESTAMP(6)
WHERE id = 9100000000462
  AND deleted = b'0';

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT DISTINCT child_grant.role_id,9100000000491,child_grant.tenant_id,'CloudMold',
       UTC_TIMESTAMP(6),'CloudMold:canonical-supply-chain-quality-navigation',
       UTC_TIMESTAMP(6),b'0'
FROM system_role_menu child_grant
JOIN system_role role
  ON role.id = child_grant.role_id
 AND role.tenant_id = child_grant.tenant_id
 AND role.deleted = b'0'
 AND role.status = 0
WHERE child_grant.menu_id = 9100000000462
  AND child_grant.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM system_role_menu parent_grant
    WHERE parent_grant.role_id = child_grant.role_id
      AND parent_grant.tenant_id = child_grant.tenant_id
      AND parent_grant.menu_id = 9100000000491
      AND parent_grant.deleted = b'0'
  );

DROP TEMPORARY TABLE cloudmold_v159_guard;
