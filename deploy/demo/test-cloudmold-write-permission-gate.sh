#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
gate="${repo_root}/deploy/demo/cloudmold-write-permission-gate.py"
fixture_root="$(mktemp -d)"
trap 'rm -rf "${fixture_root}"' EXIT

fixture_dir="${fixture_root}/cloudmold-module-fixture/server/src/main/java/example/controller/admin"
mkdir -p "${fixture_dir}"

cat >"${fixture_dir}/AllowedController.java" <<'JAVA'
package example;
class AllowedController {
    @PostMapping("/command")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:command')")
    public Object command() { return null; }
}
JAVA

python3 "${gate}" --root "${fixture_root}" >/dev/null

cat >"${fixture_dir}/DeniedController.java" <<'JAVA'
package example;
class DeniedController {
    @DeleteMapping("/unsafe")
    public void unsafe() {}
}
JAVA

if python3 "${gate}" --root "${fixture_root}" >/dev/null 2>&1; then
  echo "permission gate accepted an unguarded write endpoint" >&2
  exit 1
fi

rm "${fixture_dir}/DeniedController.java"

cat >"${fixture_dir}/QueryOnlyController.java" <<'JAVA'
package example;
class QueryOnlyController {
    @PutMapping("/unsafe")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:query')")
    public void unsafe() {}
}
JAVA

if python3 "${gate}" --root "${fixture_root}" >/dev/null 2>&1; then
  echo "permission gate accepted a write endpoint guarded only by query permission" >&2
  exit 1
fi

python3 "${gate}" --root "${repo_root}"
