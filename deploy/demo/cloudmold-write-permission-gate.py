#!/usr/bin/env python3
"""Fail when a CloudMold admin write endpoint lacks an explicit write permission."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys


WRITE_MAPPING = re.compile(r"@(Post|Put|Patch|Delete)Mapping(?:\s|\(|$)")
PUBLIC_DECLARATION = re.compile(r"^\s*public\s+")
PRE_AUTHORIZE = re.compile(r"@PreAuthorize\((.+)\)")
CLOUDMOLD_PERMISSION = re.compile(r"cloudmold:[a-z0-9:-]+")
METHOD_NAME = re.compile(r"\b([A-Za-z_]\w*)\s*\(")
READ_METHOD_PREFIXES = (
    "calculate",
    "estimate",
    "get",
    "list",
    "preview",
    "query",
    "read",
    "require",
    "resolve",
    "search",
    "validate",
)


def controller_files(root: Path) -> list[Path]:
    return sorted(
        path
        for path in root.glob(
            "cloudmold-module-*/**/src/main/java/**/controller/admin/**/*.java"
        )
        if path.is_file()
    )


def inspect_controller(
    path: Path, root: Path
) -> tuple[int, int, list[dict[str, object]]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    endpoint_count = 0
    post_query_count = 0
    failures: list[dict[str, object]] = []
    for index, line in enumerate(lines):
        if not WRITE_MAPPING.search(line):
            continue
        endpoint_count += 1
        declaration_index = None
        for candidate in range(index + 1, min(index + 24, len(lines))):
            if PUBLIC_DECLARATION.search(lines[candidate]):
                declaration_index = candidate
                break
        if declaration_index is None:
            failures.append(
                {
                    "file": str(path.relative_to(root)),
                    "line": index + 1,
                    "reason": "write mapping has no nearby Java method declaration",
                }
            )
            continue
        annotation_text = " ".join(lines[index : declaration_index + 1])
        authorization = PRE_AUTHORIZE.search(annotation_text)
        permissions = (
            CLOUDMOLD_PERMISSION.findall(authorization.group(1))
            if authorization
            else []
        )
        if not authorization:
            reason = "write endpoint has no @PreAuthorize annotation"
        elif not permissions:
            reason = "write endpoint authorization has no CloudMold permission"
        elif all(permission.endswith(":query") for permission in permissions):
            signature = " ".join(lines[declaration_index : declaration_index + 6])
            method_names = METHOD_NAME.findall(signature.split("{", 1)[0])
            method_name = method_names[-1] if method_names else ""
            if method_name.startswith(READ_METHOD_PREFIXES):
                post_query_count += 1
                continue
            reason = "write endpoint is guarded only by query permission"
        else:
            continue
        failures.append(
            {
                "file": str(path.relative_to(root)),
                "line": index + 1,
                "reason": reason,
            }
        )
    return endpoint_count, post_query_count, failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[2],
        help="yudao-cloud repository root",
    )
    args = parser.parse_args()
    root = args.root.expanduser().resolve()
    files = controller_files(root)
    if not files:
        print(
            json.dumps(
                {"status": "failed", "reason": "no CloudMold admin controllers found"}
            ),
            file=sys.stderr,
        )
        return 1

    endpoint_count = 0
    post_query_count = 0
    failures: list[dict[str, object]] = []
    for path in files:
        count, query_count, file_failures = inspect_controller(path, root)
        endpoint_count += count
        post_query_count += query_count
        failures.extend(file_failures)

    result = {
        "status": "passed" if not failures else "failed",
        "controller_files": len(files),
        "write_endpoints": endpoint_count,
        "post_query_endpoints": post_query_count,
        "failures": failures,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
