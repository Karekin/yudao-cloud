#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
SQL_ROOT=${CLOUDMOLD_SQL_ROOT:-"$REPO_ROOT/sql/cloudmold"}
MANIFEST_FILE=${CLOUDMOLD_MANIFEST_FILE:-"$SCRIPT_DIR/cloudmold-migrations.sha256"}

usage() {
  cat <<'EOF' >&2
usage: cloudmold-migration-manifest.sh [--write|--check]
EOF
}

hash_file() {
  local file=$1

  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
    return 0
  fi

  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
    return 0
  fi

  if command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 -r "$file" | awk '{print $1}'
    return 0
  fi

  echo 'no SHA-256 tool found (need sha256sum, shasum, or openssl)' >&2
  exit 69
}

list_sql_files() {
  if [[ ! -d "$SQL_ROOT" ]]; then
    echo "sql root not found: $SQL_ROOT" >&2
    exit 66
  fi

  (
    cd "$SQL_ROOT"
    find . -type f -name '*.sql' -print \
      | sed 's|^\./||' \
      | LC_ALL=C sort
  )
}

build_manifest_stream() {
  local relative_path
  local file_count=0

  while IFS= read -r relative_path; do
    [[ -z "$relative_path" ]] && continue
    printf '%s  %s\n' "$(hash_file "$SQL_ROOT/$relative_path")" "$relative_path"
    file_count=$((file_count + 1))
  done < <(list_sql_files)

  if (( file_count == 0 )); then
    echo "no SQL files found under $SQL_ROOT" >&2
    exit 65
  fi
}

validate_manifest_file() {
  local manifest=$1
  local invalid
  local duplicate_paths

  if [[ ! -f "$manifest" ]]; then
    echo "manifest not found: $manifest" >&2
    exit 66
  fi

  invalid=$(
    awk '!match($0, /^[0-9a-f]{64}  .+$/) { print NR ":" $0 }' "$manifest"
  )
  if [[ -n "$invalid" ]]; then
    echo "manifest contains invalid lines:" >&2
    echo "$invalid" >&2
    exit 65
  fi

  duplicate_paths=$(
    awk '{print substr($0, 67)}' "$manifest" \
      | LC_ALL=C sort \
      | uniq -d
  )
  if [[ -n "$duplicate_paths" ]]; then
    echo "manifest contains duplicate relative paths:" >&2
    echo "$duplicate_paths" >&2
    exit 65
  fi
}

to_path_hash_pairs() {
  local manifest=$1

  awk '{print substr($0, 67) "\t" substr($0, 1, 64)}' "$manifest" | LC_ALL=C sort
}

write_manifest() {
  local tmp_manifest
  local file_count

  mkdir -p "$(dirname "$MANIFEST_FILE")"
  tmp_manifest=$(mktemp "${MANIFEST_FILE}.tmp.XXXXXX")
  build_manifest_stream >"$tmp_manifest"
  mv "$tmp_manifest" "$MANIFEST_FILE"
  file_count=$(wc -l <"$MANIFEST_FILE" | tr -d ' ')
  echo "cloudmold migration manifest written: files=$file_count manifest=$MANIFEST_FILE"
}

check_manifest() {
  local expected_manifest=$MANIFEST_FILE
  local manifest_tmp_dir
  local actual_manifest
  local expected_pairs
  local actual_pairs
  local missing_paths
  local extra_paths
  local changed_paths
  local failed=false

  validate_manifest_file "$expected_manifest"

  manifest_tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/cloudmold-migration-manifest.XXXXXX")
  actual_manifest="$manifest_tmp_dir/actual"
  expected_pairs="$manifest_tmp_dir/expected"
  actual_pairs="$manifest_tmp_dir/pairs"
  trap 'rm -rf "$manifest_tmp_dir"' EXIT

  build_manifest_stream >"$actual_manifest"
  to_path_hash_pairs "$expected_manifest" >"$expected_pairs"
  to_path_hash_pairs "$actual_manifest" >"$actual_pairs"

  missing_paths=$(comm -23 <(cut -f1 "$expected_pairs") <(cut -f1 "$actual_pairs") || true)
  extra_paths=$(comm -13 <(cut -f1 "$expected_pairs") <(cut -f1 "$actual_pairs") || true)
  changed_paths=$(
    join -t $'\t' "$expected_pairs" "$actual_pairs" \
      | awk -F '\t' '$2 != $3 { print $1 "\t" $2 "\t" $3 }'
  )

  if [[ -n "$missing_paths" || -n "$extra_paths" || -n "$changed_paths" ]]; then
    echo 'cloudmold migration manifest check failed' >&2

    if [[ -n "$missing_paths" ]]; then
      echo 'missing SQL files:' >&2
      while IFS= read -r path; do
        [[ -z "$path" ]] && continue
        echo "  - $path" >&2
      done <<<"$missing_paths"
      failed=true
    fi

    if [[ -n "$extra_paths" ]]; then
      echo 'unexpected SQL files:' >&2
      while IFS= read -r path; do
        [[ -z "$path" ]] && continue
        echo "  + $path" >&2
      done <<<"$extra_paths"
      failed=true
    fi

    if [[ -n "$changed_paths" ]]; then
      echo 'changed SQL files:' >&2
      while IFS=$'\t' read -r path expected_hash actual_hash; do
        [[ -z "$path" ]] && continue
        echo "  * $path" >&2
        echo "    expected: $expected_hash" >&2
        echo "    actual:   $actual_hash" >&2
      done <<<"$changed_paths"
      failed=true
    fi
  fi

  if [[ "$failed" == true ]]; then
    exit 1
  fi

  rm -rf -- "$manifest_tmp_dir"
  trap - EXIT
  echo "cloudmold migration manifest check passed: files=$(wc -l <"$expected_manifest" | tr -d ' ') manifest=$expected_manifest"
}

case "${1:-}" in
  --write)
    write_manifest
    ;;
  --check)
    check_manifest
    ;;
  *)
    usage
    exit 64
    ;;
esac
