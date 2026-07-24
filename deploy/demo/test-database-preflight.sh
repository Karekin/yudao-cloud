#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PREFLIGHT="$SCRIPT_DIR/database-preflight.sh"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

cat >"$TMP_DIR/mysql" <<'FAKE_MYSQL'
#!/usr/bin/env bash
set -euo pipefail

query=''
for argument in "$@"; do
  case "$argument" in
    --execute=*) query=${argument#--execute=} ;;
  esac
done

if [[ -z "$query" ]]; then
  cat >/dev/null
  printf '%s' "${CLOUDMOLD_FAKE_NAVIGATION_ROWS:-}"
  exit 0
fi

case "$query" in
  'SELECT VERSION();')
    echo '8.0.46'
    ;;
  'SELECT DATABASE();')
    echo "${CLOUDMOLD_DB_NAME}"
    ;;
  *information_schema.tables*)
    "$CLOUDMOLD_PREFLIGHT_SCRIPT" --list-required-tables \
      | grep -Fvx -- "${CLOUDMOLD_FAKE_MISSING_TABLE:-__none__}" || true
    ;;
  *information_schema.columns*)
    "$CLOUDMOLD_PREFLIGHT_SCRIPT" --list-required-columns
    ;;
  *information_schema.statistics*)
    "$CLOUDMOLD_PREFLIGHT_SCRIPT" --list-required-indexes
    ;;
  *information_schema.table_constraints*)
    "$CLOUDMOLD_PREFLIGHT_SCRIPT" --list-required-constraints
    ;;
  *)
    echo "unexpected query: $query" >&2
    exit 65
    ;;
esac
FAKE_MYSQL
chmod +x "$TMP_DIR/mysql"

export CLOUDMOLD_DB_HOST=demo-db.internal
export CLOUDMOLD_DB_NAME=ruoyi-vue-pro
export CLOUDMOLD_DB_USERNAME=preflight
export CLOUDMOLD_DB_PASSWORD=not-a-real-secret
export CLOUDMOLD_PREFLIGHT_SCRIPT="$PREFLIGHT"
export MYSQL_BIN="$TMP_DIR/mysql"

"$PREFLIGHT" | grep -Fq 'database preflight passed'

if CLOUDMOLD_FAKE_MISSING_TABLE=member_user "$PREFLIGHT" >"$TMP_DIR/missing.out" 2>&1; then
  echo 'expected the preflight to reject a missing table' >&2
  exit 1
fi
grep -Fq 'missing tables' "$TMP_DIR/missing.out"
grep -Fq 'member_user' "$TMP_DIR/missing.out"

if CLOUDMOLD_FAKE_NAVIGATION_ROWS='navigation_violation' \
  "$PREFLIGHT" >"$TMP_DIR/navigation.out" 2>&1; then
  echo 'expected the preflight to reject navigation DQC rows' >&2
  exit 1
fi
grep -Fq 'business_navigation_preflight' "$TMP_DIR/navigation.out"
grep -Fq 'navigation_violation' "$TMP_DIR/navigation.out"

if CLOUDMOLD_BUSINESS_NAVIGATION_PRECHECK_FILE="$TMP_DIR/missing.sql" \
  "$PREFLIGHT" >"$TMP_DIR/missing-navigation-precheck.out" 2>&1; then
  echo 'expected the preflight to reject a missing navigation check' >&2
  exit 1
fi
grep -Fq 'missing V82 navigation precheck file' \
  "$TMP_DIR/missing-navigation-precheck.out"

echo 'database preflight regression tests passed'
