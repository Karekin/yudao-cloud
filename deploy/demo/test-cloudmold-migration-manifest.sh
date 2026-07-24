#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
MANIFEST_SCRIPT="$SCRIPT_DIR/cloudmold-migration-manifest.sh"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "$1" >&2
  exit 1
}

run_manifest() {
  local sql_root=$1
  local manifest_file=$2
  shift 2

  CLOUDMOLD_SQL_ROOT="$sql_root" \
  CLOUDMOLD_MANIFEST_FILE="$manifest_file" \
    "$MANIFEST_SCRIPT" "$@"
}

make_fixture_tree() {
  local repo_dir=$1

  mkdir -p "$repo_dir/sql/cloudmold/rollback" "$repo_dir/sql/cloudmold/tests"
  cat >"$repo_dir/sql/cloudmold/V20260101_01__alpha.sql" <<'EOF'
CREATE TABLE alpha (
  id BIGINT PRIMARY KEY
);
EOF
  cat >"$repo_dir/sql/cloudmold/rollback/V20260101_01__alpha.sql" <<'EOF'
DROP TABLE alpha;
EOF
  cat >"$repo_dir/sql/cloudmold/tests/V20260101_01__alpha_dqc.sql" <<'EOF'
SELECT 1;
EOF
}

assert_check_fails() {
  local output_file=$1
  shift

  if "$@" >"$output_file" 2>&1; then
    fail "expected manifest check to fail"
  fi
}

FIXTURE_REPO="$TMP_DIR/fixture-repo"
FIXTURE_MANIFEST="$TMP_DIR/fixture-manifest.sha256"

make_fixture_tree "$FIXTURE_REPO"

run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --write \
  | grep -Fq 'cloudmold migration manifest written'
run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --check \
  | grep -Fq 'cloudmold migration manifest check passed'

printf '\n-- tampered\n' >>"$FIXTURE_REPO/sql/cloudmold/V20260101_01__alpha.sql"
assert_check_fails "$TMP_DIR/tampered.out" \
  run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --check
grep -Fq 'changed SQL files:' "$TMP_DIR/tampered.out"
grep -Fq 'V20260101_01__alpha.sql' "$TMP_DIR/tampered.out"

make_fixture_tree "$FIXTURE_REPO"
run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --write >/dev/null

cat >"$FIXTURE_REPO/sql/cloudmold/V20260102_02__beta.sql" <<'EOF'
CREATE TABLE beta (
  id BIGINT PRIMARY KEY
);
EOF
assert_check_fails "$TMP_DIR/extra.out" \
  run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --check
grep -Fq 'unexpected SQL files:' "$TMP_DIR/extra.out"
grep -Fq 'V20260102_02__beta.sql' "$TMP_DIR/extra.out"

rm -f "$FIXTURE_REPO/sql/cloudmold/V20260102_02__beta.sql"
rm -f "$FIXTURE_REPO/sql/cloudmold/tests/V20260101_01__alpha_dqc.sql"
assert_check_fails "$TMP_DIR/missing.out" \
  run_manifest "$FIXTURE_REPO/sql/cloudmold" "$FIXTURE_MANIFEST" --check
grep -Fq 'missing SQL files:' "$TMP_DIR/missing.out"
grep -Fq 'tests/V20260101_01__alpha_dqc.sql' "$TMP_DIR/missing.out"

run_manifest "$REPO_ROOT/sql/cloudmold" "$SCRIPT_DIR/cloudmold-migrations.sha256" --check \
  | grep -Fq 'cloudmold migration manifest check passed'

if find "$REPO_ROOT" -maxdepth 1 -type f -name 'cloudmold-migration-manifest.*' \
  | grep -q .; then
  fail 'manifest check leaked temporary files into the repository'
fi

echo 'cloudmold migration manifest regression tests passed'
