#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)

list_required_tables() {
  {
    printf '%s\n' \
      system_users infra_config \
      member_user \
      pay_order pay_order_extension pay_refund pay_transfer \
      product_spu product_sku \
      trade_order trade_order_item trade_order_log \
      erp_product erp_stock erp_purchase_order erp_purchase_in erp_warehouse
    sed -nE 's/.*CREATE TABLE IF NOT EXISTS [`]?([^` (]+).*/\1/p' \
      "$REPO_ROOT"/sql/cloudmold/*.sql
  } | LC_ALL=C sort -u
}

list_required_columns() {
  printf '%s\n' \
    cloudmold_event_outbox:source_system \
    cloudmold_order_cancellation_saga:cancellation_mode \
    cloudmold_fulfillment_order:cancellation_saga_id \
    member_user:email \
    pay_order:user_id \
    pay_order:user_type \
    cloudmold_after_sale_case:status \
    cloudmold_after_sale_resolution_saga:status
}

list_required_indexes() {
  printf '%s\n' \
    member_user:idx_mobile \
    pay_order:idx_no \
    product_sku:idx_spu_id \
    trade_order:idx_user_id
}

list_required_constraints() {
  printf '%s\n' \
    ck_cm_after_sale_status \
    fk_cm_after_sale_resolution_case
}

case "${1:-}" in
  --list-required-tables)
    list_required_tables
    exit 0
    ;;
  --list-required-columns)
    list_required_columns
    exit 0
    ;;
  --list-required-indexes)
    list_required_indexes
    exit 0
    ;;
  --list-required-constraints)
    list_required_constraints
    exit 0
    ;;
  "") ;;
  *)
    echo "usage: $0 [--list-required-tables|--list-required-columns|--list-required-indexes|--list-required-constraints]" >&2
    exit 64
    ;;
esac

"$SCRIPT_DIR/cloudmold-migration-manifest.sh" --check

: "${CLOUDMOLD_DB_HOST:?set CLOUDMOLD_DB_HOST}"
: "${CLOUDMOLD_DB_NAME:?set CLOUDMOLD_DB_NAME}"
: "${CLOUDMOLD_DB_USERNAME:?set CLOUDMOLD_DB_USERNAME}"
: "${CLOUDMOLD_DB_PASSWORD:?set CLOUDMOLD_DB_PASSWORD}"

CLOUDMOLD_DB_PORT=${CLOUDMOLD_DB_PORT:-3306}
CLOUDMOLD_DB_SSL_MODE=${CLOUDMOLD_DB_SSL_MODE:-PREFERRED}
MYSQL_BIN=${MYSQL_BIN:-mysql}

case "$CLOUDMOLD_DB_SSL_MODE" in
  DISABLED|PREFERRED|REQUIRED|VERIFY_CA|VERIFY_IDENTITY) ;;
  *)
    echo "invalid CLOUDMOLD_DB_SSL_MODE: $CLOUDMOLD_DB_SSL_MODE" >&2
    exit 64
    ;;
esac

if ! command -v "$MYSQL_BIN" >/dev/null 2>&1 && [[ ! -x "$MYSQL_BIN" ]]; then
  echo "mysql client not found: $MYSQL_BIN" >&2
  exit 69
fi

MYSQL_ARGS=(
  --host="$CLOUDMOLD_DB_HOST"
  --port="$CLOUDMOLD_DB_PORT"
  --user="$CLOUDMOLD_DB_USERNAME"
  --database="$CLOUDMOLD_DB_NAME"
  --batch
  --skip-column-names
  --connect-timeout=5
  --ssl-mode="$CLOUDMOLD_DB_SSL_MODE"
)

query() {
  MYSQL_PWD="$CLOUDMOLD_DB_PASSWORD" "$MYSQL_BIN" "${MYSQL_ARGS[@]}" --execute="$1"
}

assert_inventory() {
  local kind=$1
  local expected=$2
  local actual=$3
  local missing=()
  local item

  while IFS= read -r item; do
    [[ -z "$item" ]] && continue
    if ! grep -Fqx -- "$item" <<<"$actual"; then
      missing+=("$item")
    fi
  done <<<"$expected"

  if (( ${#missing[@]} > 0 )); then
    echo "database preflight failed: missing $kind" >&2
    printf '  - %s\n' "${missing[@]}" >&2
    return 1
  fi
}

assert_zero_rows() {
  local name=$1
  local file=$2
  local result

  result=$(
    MYSQL_PWD="$CLOUDMOLD_DB_PASSWORD" \
    "$MYSQL_BIN" "${MYSQL_ARGS[@]}" \
      <"$file"
  )

  result=$(printf '%s\n' "$result" | sed '/^--/d;/^#/d;/^$/d')
  if [[ -n "$result" ]]; then
    echo "database preflight failed: navigation zero-row check '$name' returned data" >&2
    echo "$result" >&2
    return 1
  fi
}

version=$(query 'SELECT VERSION();')
major_version=${version%%.*}
if [[ ! "$major_version" =~ ^[0-9]+$ ]] || (( major_version < 8 )); then
  echo "database preflight failed: MySQL 8 or newer is required (found $version)" >&2
  exit 1
fi

selected_database=$(query 'SELECT DATABASE();')
if [[ "$selected_database" != "$CLOUDMOLD_DB_NAME" ]]; then
  echo "database preflight failed: selected database mismatch" >&2
  exit 1
fi

tables=$(query "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE();")
columns=$(query "SELECT CONCAT(table_name, ':', column_name) FROM information_schema.columns WHERE table_schema = DATABASE();")
indexes=$(query "SELECT DISTINCT CONCAT(table_name, ':', index_name) FROM information_schema.statistics WHERE table_schema = DATABASE();")
constraints=$(query "SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = DATABASE();")

assert_inventory tables "$(list_required_tables)" "$tables"
assert_inventory columns "$(list_required_columns)" "$columns"
assert_inventory indexes "$(list_required_indexes)" "$indexes"
assert_inventory constraints "$(list_required_constraints)" "$constraints"

if [[ "${CLOUDMOLD_PRECHECK_BUSINESS_NAVIGATION:-true}" == "true" ]]; then
  V82_PRECHECK="${CLOUDMOLD_BUSINESS_NAVIGATION_PRECHECK_FILE:-${REPO_ROOT}/sql/cloudmold/V20260724_82__business_navigation_preflight.sql}"
  if [[ ! -f "$V82_PRECHECK" ]]; then
    echo "database preflight failed: missing V82 navigation precheck file: $V82_PRECHECK" >&2
    exit 1
  fi

  assert_zero_rows 'business_navigation_preflight' "$V82_PRECHECK"
fi

table_count=$(list_required_tables | wc -l | tr -d ' ')
echo "database preflight passed: schema=$selected_database mysql=$version required_tables=$table_count"
