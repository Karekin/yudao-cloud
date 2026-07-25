#!/usr/bin/env bash
set -euo pipefail

snapshot="${YUDDAO_BPM_SQL_SNAPSHOT:-}"
expected_sha="${YUDDAO_BPM_SQL_SHA256:-f20a92a43431ee4243bbeb9556da6c01ea88ff4ea584c366448f0bcc2e0ce9a7}"

if [[ -z "${snapshot}" || ! -f "${snapshot}" ]]; then
  echo "YUDDAO_BPM_SQL_SNAPSHOT must point to the reviewed upstream yudao BPM MySQL snapshot" >&2
  exit 1
fi

actual_sha="$(shasum -a 256 "${snapshot}" | awk '{print $1}')"
if [[ "${actual_sha}" != "${expected_sha}" ]]; then
  echo "upstream BPM snapshot checksum mismatch: expected=${expected_sha} actual=${actual_sha}" >&2
  exit 1
fi

python3 - "${snapshot}" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text()
allowed = {
    "bpm_category",
    "bpm_form",
    "bpm_oa_leave",
    "bpm_process_definition_info",
    "bpm_process_expression",
    "bpm_process_instance_copy",
    "bpm_process_listener",
    "bpm_user_group",
}
matches = list(re.finditer(r"CREATE TABLE `([^`]+)`\s*\(.*?\n\)[^;]*;", source, re.S))
tables = {match.group(1) for match in matches}
if tables != allowed:
    raise SystemExit(
        f"unexpected upstream BPM table set: expected={sorted(allowed)} actual={sorted(tables)}"
    )

print("SET NAMES utf8mb4;")
for match in matches:
    name = match.group(1)
    statement = match.group(0)
    statement = statement.replace(f"CREATE TABLE `{name}`", f"CREATE TABLE IF NOT EXISTS `{name}`", 1)
    print(statement)
PY
