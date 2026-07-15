#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "${script_dir}/../.." && pwd)"
mysql_container="${MYSQL_CONTAINER:-yudao-mysql}"
mysql_database="${MYSQL_DATABASE:-ruoyi-vue-pro}"
mysql_user="${MYSQL_USER:-root}"
mysql_password="${MYSQL_PASSWORD:-123456}"
fixture_sql="${repo_dir}/cloudmold-module-warehouse/cloudmold-module-warehouse-server/src/test/resources/sql/single_warehouse_source_fixture.sql"
dqc_sql="${repo_dir}/sql/cloudmold/tests/canonical_warehouse_source_mapping_dqc.sql"

mysql_client() {
  docker exec -i -e MYSQL_PWD="${mysql_password}" "${mysql_container}" \
    mysql -u"${mysql_user}" -D "${mysql_database}" --batch --raw "$@"
}

required_tables="$(mysql_client -N -e "
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema=DATABASE()
    AND table_name IN ('erp_warehouse','wms_warehouse','cloudmold_warehouse',
      'cloudmold_warehouse_zone','cloudmold_warehouse_location',
      'cloudmold_warehouse_source_mapping','cloudmold_event_outbox');")"
if [[ "${required_tables}" != "7" ]]; then
  echo "warehouse fixture requires V13, V14, V15, the event outbox, and ERP/WMS source tables" >&2
  exit 1
fi

mysql_client < "${fixture_sql}"

expect_constraint_failure() {
  local name="$1"
  local sql="$2"
  if mysql_client -e "${sql}" >/dev/null 2>&1; then
    echo "expected ${name} to be rejected" >&2
    exit 1
  fi
  echo "PASS ${name}"
}

expect_constraint_failure "duplicate qualified source mapping" "
  INSERT INTO cloudmold_warehouse_source_mapping
    (mapping_id,tenant_id,source_system,source_type,source_id,canonical_type,canonical_id,
     warehouse_id,valid_from,verification_ref,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000041',991501,'ERP','WAREHOUSE','99150101','WAREHOUSE',
     'f1500000-0000-4000-8000-000000000001','f1500000-0000-4000-8000-000000000001',
     '2026-07-15 00:00:00.000000','negative-test:duplicate','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));"

expect_constraint_failure "cross-tenant canonical mapping" "
  INSERT INTO cloudmold_warehouse_source_mapping
    (mapping_id,tenant_id,source_system,source_type,source_id,canonical_type,canonical_id,
     warehouse_id,valid_from,verification_ref,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000042',991502,'ERP','WAREHOUSE','cross-tenant','WAREHOUSE',
     'f1500000-0000-4000-8000-000000000001','f1500000-0000-4000-8000-000000000001',
     '2026-07-15 00:00:00.000000','negative-test:cross-tenant','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));"

expect_constraint_failure "canonical identifier source-key reuse" "
  INSERT INTO cloudmold_warehouse_source_mapping
    (mapping_id,tenant_id,source_system,source_type,source_id,canonical_type,canonical_id,
     warehouse_id,valid_from,verification_ref,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000043',991501,'TEST','WAREHOUSE',
     'f1500000-0000-4000-8000-000000000001','WAREHOUSE',
     'f1500000-0000-4000-8000-000000000001','f1500000-0000-4000-8000-000000000001',
     '2026-07-15 00:00:00.000000','negative-test:source-key-reuse','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));"

expect_constraint_failure "non-normalized qualified source identity" "
  INSERT INTO cloudmold_warehouse_source_mapping
    (mapping_id,tenant_id,source_system,source_type,source_id,canonical_type,canonical_id,
     warehouse_id,valid_from,verification_ref,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000044',991501,'erp','WAREHOUSE','source-with-spaces ',
     'WAREHOUSE','f1500000-0000-4000-8000-000000000001',
     'f1500000-0000-4000-8000-000000000001','2026-07-15 00:00:00.000000',
     'negative-test:normalization','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));"

expect_constraint_failure "cross-warehouse location hierarchy" "
  START TRANSACTION;
  INSERT INTO cloudmold_warehouse
    (warehouse_id,tenant_id,warehouse_code,name,warehouse_type,timezone,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000051',991501,'CM-WH-NEGATIVE','Negative Warehouse',
     'FULFILLMENT','Asia/Shanghai','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
  INSERT INTO cloudmold_warehouse_zone
    (zone_id,tenant_id,warehouse_id,zone_code,name,zone_type,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000052',991501,
     'f1500000-0000-4000-8000-000000000051','NEGATIVE-ZONE','Negative Zone','STORAGE',
     'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
  INSERT INTO cloudmold_warehouse_location
    (location_id,tenant_id,warehouse_id,zone_id,location_code,name,location_type,
     allow_item_mixing,allow_lot_mixing,status,version,created_at,updated_at)
  VALUES
    ('f1500000-0000-4000-8000-000000000053',991501,
     'f1500000-0000-4000-8000-000000000001','f1500000-0000-4000-8000-000000000052',
     'NEGATIVE-LOCATION','Negative Location','STORAGE',0,0,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
  ROLLBACK;"

mysql_client < "${dqc_sql}"

summary="$(mysql_client -N -e "
  SELECT CONCAT_WS(',',
    (SELECT COUNT(*) FROM cloudmold_warehouse WHERE tenant_id=991501),
    (SELECT COUNT(*) FROM cloudmold_warehouse_zone WHERE tenant_id=991501),
    (SELECT COUNT(*) FROM cloudmold_warehouse_location WHERE tenant_id=991501),
    (SELECT COUNT(*) FROM cloudmold_warehouse_source_mapping WHERE tenant_id=991501),
    (SELECT COUNT(*) FROM cloudmold_event_outbox
      WHERE tenant_id=991501 AND source_system='cloudmold-warehouse'));"
)"
if [[ "${summary}" != "1,1,1,2,8" ]]; then
  echo "unexpected fixture summary ${summary}; expected 1,1,1,2,8" >&2
  exit 1
fi
echo "PASS idempotent warehouse fixture summary ${summary}"
