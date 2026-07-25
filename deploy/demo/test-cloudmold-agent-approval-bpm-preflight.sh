#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFLIGHT="$ROOT_DIR/deploy/demo/cloudmold-agent-approval-bpm-preflight.sh"
SOURCE_MANIFEST="$ROOT_DIR/deploy/demo/cloudmold-agent-approval-v1.manifest.json"
SOURCE_BPMN="$ROOT_DIR/cloudmold-module-agent-control/cloudmold-module-agent-control-server/src/main/resources/approval-workflow/cloudmold-agent-approval-v1.bpmn20.xml"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

write_fixture() {
  local transform="$1"
  python3 - "$SOURCE_MANIFEST" "$SOURCE_BPMN" "$TEST_DIR" "$transform" <<'PY'
import hashlib
import json
import pathlib
import sys

manifest_path = pathlib.Path(sys.argv[1])
source_bpmn = pathlib.Path(sys.argv[2])
test_dir = pathlib.Path(sys.argv[3])
transform = sys.argv[4]

content = source_bpmn.read_text()
if transform == "missing-task":
    content = content.replace('id="approval_review"', 'id="removed_approval_task"')
elif transform == "missing-candidate-strategy":
    content = content.replace('flowable:candidateStrategy="35"', 'flowable:candidateStrategy="30"')

bpmn_path = test_dir / "approval.bpmn20.xml"
bpmn_path.write_text(content)
manifest = json.loads(manifest_path.read_text())
manifest["bpmn_resource"] = str(bpmn_path)
manifest["bpmn_sha256"] = hashlib.sha256(content.encode()).hexdigest()
(test_dir / "manifest.json").write_text(json.dumps(manifest))
PY
}

write_fixture valid
CLOUDMOLD_AGENT_APPROVAL_MANIFEST="$TEST_DIR/manifest.json" "$PREFLIGHT" >/dev/null

write_fixture missing-task
if CLOUDMOLD_AGENT_APPROVAL_MANIFEST="$TEST_DIR/manifest.json" "$PREFLIGHT" >/dev/null 2>&1; then
  echo "preflight accepted a BPMN without the approval task definition key" >&2
  exit 1
fi

write_fixture missing-candidate-strategy
if CLOUDMOLD_AGENT_APPROVAL_MANIFEST="$TEST_DIR/manifest.json" "$PREFLIGHT" >/dev/null 2>&1; then
  echo "preflight accepted a BPMN without explicit START_USER_SELECT approvers" >&2
  exit 1
fi

echo "cloudmold agent approval BPM preflight regression tests passed"
