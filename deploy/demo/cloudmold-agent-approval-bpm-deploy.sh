#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFLIGHT="$ROOT_DIR/deploy/demo/cloudmold-agent-approval-bpm-preflight.sh"
MANIFEST="$ROOT_DIR/deploy/demo/cloudmold-agent-approval-v1.manifest.json"

"$PREFLIGHT" >/tmp/cloudmold-agent-approval-preflight.json

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "missing required env: $name" >&2
    exit 1
  fi
}

require_env CLOUDMOLD_BASE_URL
require_env CLOUDMOLD_ADMIN_TOKEN
require_env CLOUDMOLD_TENANT_ID

python3 - "$ROOT_DIR" "$MANIFEST" <<'PY'
import json
import os
import pathlib
import sys
import urllib.parse
import urllib.request

root = pathlib.Path(sys.argv[1])
manifest = json.loads(pathlib.Path(sys.argv[2]).read_text())
bpmn = (root / manifest["bpmn_resource"]).read_text()

base = os.environ["CLOUDMOLD_BASE_URL"].rstrip("/")
token = os.environ["CLOUDMOLD_ADMIN_TOKEN"]
headers = {
    "Authorization": f"Bearer {token}",
    "tenant-id": os.environ["CLOUDMOLD_TENANT_ID"],
    "Content-Type": "application/json",
}

def request(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        result = json.loads(resp.read().decode())
    if result.get("code") != 0:
        raise RuntimeError(f"BPM API failed method={method} path={path} code={result.get('code')} msg={result.get('msg')}")
    return result

categories = request("GET", "/admin-api/bpm/category/simple-list").get("data") or []
if not any(item.get("code") == manifest["category"] for item in categories):
    request("POST", "/admin-api/bpm/category/create", {
        "name": "CloudMold Agent Control",
        "description": "Human approval evidence for governed CloudMold agent actions",
        "code": manifest["category"],
        "status": 0,
        "sort": 100,
    })

model_name = urllib.parse.quote(manifest["model_name"])
models = request("GET", f"/admin-api/bpm/model/list?name={model_name}")
items = models.get("data") or []
model = next((item for item in items if item.get("key") == manifest["model_key"]), None)
payload = {
    "id": model.get("id") if model else None,
    "key": manifest["model_key"],
    "name": manifest["model_name"],
    "category": manifest["category"],
    "type": manifest["model_type"],
    "formType": manifest["form_type"],
    "formCustomCreatePath": "/cloudmold/agent-control/approvals",
    "formCustomViewPath": "/cloudmold/agent-control/approvals",
    "visible": True,
    "managerUserIds": [1],
    "bpmnXml": bpmn
}
if model is None:
    created = request("POST", "/admin-api/bpm/model/create", payload)
    model_id = created["data"]
else:
    request("PUT", "/admin-api/bpm/model/update", payload)
    model_id = model["id"]

request("POST", f"/admin-api/bpm/model/deploy?id={model_id}")
definition = request("GET", f"/admin-api/bpm/process-definition/get?key={manifest['process_definition_key']}")
print(json.dumps({
    "model_id": model_id,
    "definition": definition.get("data"),
    "manifest": manifest
}, ensure_ascii=False, indent=2))
PY
