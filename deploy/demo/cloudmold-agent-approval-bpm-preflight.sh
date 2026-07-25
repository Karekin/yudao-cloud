#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MANIFEST="${CLOUDMOLD_AGENT_APPROVAL_MANIFEST:-$ROOT_DIR/deploy/demo/cloudmold-agent-approval-v1.manifest.json}"
SERVER_POM="${CLOUDMOLD_AGENT_APPROVAL_SERVER_POM:-$ROOT_DIR/yudao-server/pom.xml}"

python3 - "$ROOT_DIR" "$MANIFEST" "$SERVER_POM" <<'PY'
import hashlib
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
manifest_path = pathlib.Path(sys.argv[2])
server_pom = pathlib.Path(sys.argv[3])

manifest = json.loads(manifest_path.read_text())
bpmn_path = root / manifest["bpmn_resource"]
errors = []
bpmn_root = None

if not bpmn_path.exists():
    errors.append(f"missing BPMN resource: {bpmn_path}")
else:
    content = bpmn_path.read_text()
    sha = hashlib.sha256(content.encode()).hexdigest()
    if manifest.get("bpmn_sha256") and manifest["bpmn_sha256"] != sha:
        errors.append(f"BPMN sha256 mismatch: manifest={manifest['bpmn_sha256']} actual={sha}")
    try:
        bpmn_root = ET.fromstring(content)
    except ET.ParseError as exc:
        errors.append(f"BPMN XML is invalid: {exc}")
        bpmn_root = None
if bpmn_root is not None:
    processes = [
        element for element in bpmn_root.iter()
        if element.tag.endswith("process")
        and element.attrib.get("id") == manifest["process_definition_key"]
    ]
    if len(processes) != 1:
        errors.append("process definition key must identify exactly one BPMN process")
    approval_tasks = [
        element for element in bpmn_root.iter()
        if element.tag.endswith("userTask")
        and element.attrib.get("id") == manifest["approval_task_definition_key"]
    ]
    if len(approval_tasks) != 1:
        errors.append("approval task definition key must identify exactly one BPMN user task")
    elif not any(
        name.endswith("candidateStrategy") and value == "35"
        for name, value in approval_tasks[0].attrib.items()
    ):
        errors.append("BPMN approval task is not configured for explicit START_USER_SELECT approvers")
if manifest["model_key"] != manifest["process_definition_key"]:
    errors.append("yudao BPM requires model_key to equal process_definition_key")

pom = server_pom.read_text()
if "<id>cloudmold-agent-approval-bpm</id>" not in pom:
    errors.append("missing yudao-server Maven profile cloudmold-agent-approval-bpm")
if "<artifactId>yudao-module-bpm-server</artifactId>" not in pom:
    errors.append("missing BPM server dependency in cloudmold-agent-approval-bpm profile")

if errors:
    for error in errors:
        print(f"PRECHECK_FAIL: {error}", file=sys.stderr)
    sys.exit(1)

print(json.dumps({
    "ok": True,
    "process_definition_key": manifest["process_definition_key"],
    "approval_task_definition_key": manifest["approval_task_definition_key"],
    "bpmn_resource": str(bpmn_path),
    "bpmn_sha256": hashlib.sha256(bpmn_path.read_bytes()).hexdigest(),
    "deployment_profile": manifest["deployment_profile"]
}, ensure_ascii=False))
PY
