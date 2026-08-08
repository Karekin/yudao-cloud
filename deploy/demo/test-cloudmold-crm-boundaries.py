#!/usr/bin/env python3
"""Fail closed when the CloudMold CRM runtime crosses a legacy ownership boundary."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND_ROOT = REPO_ROOT.parent / "yudao-ui-admin-vben"
CRM_ROOT = REPO_ROOT / "cloudmold-module-crm"
FRONTEND_CRM_ROOTS = (
    FRONTEND_ROOT / "apps/web-antd/src/api/cloudmold/crm",
    FRONTEND_ROOT / "apps/web-antd/src/views/cloudmold/crm",
)


def fail(message: str) -> None:
    print(f"CRM boundary violation: {message}", file=sys.stderr)
    raise SystemExit(1)


def module_names(pom: Path) -> set[str]:
    root = ET.parse(pom).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{")
    prefix = f"{{{namespace}}}" if namespace else ""
    return {
        (element.text or "").strip()
        for element in root.findall(f".//{prefix}module")
        if (element.text or "").strip()
    }


def active_dependency_artifacts(pom: Path) -> set[str]:
    root = ET.parse(pom).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{")
    prefix = f"{{{namespace}}}" if namespace else ""
    return {
        (element.text or "").strip()
        for dependency in root.findall(f".//{prefix}dependency")
        for element in dependency.findall(f"{prefix}artifactId")
        if (element.text or "").strip()
    }


def source_files(root: Path, suffixes: tuple[str, ...]) -> list[Path]:
    if not root.is_dir():
        fail(f"required source root is missing: {root}")
    return sorted(path for path in root.rglob("*") if path.suffix in suffixes)


def reject_tokens(paths: list[Path], rejected: dict[str, str]) -> None:
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for token, reason in rejected.items():
            if token in text:
                try:
                    display_path = path.relative_to(REPO_ROOT.parent)
                except ValueError:
                    display_path = path
                fail(f"{display_path}: {reason} ({token})")


def main() -> None:
    if "cloudmold-module-crm" not in module_names(REPO_ROOT / "pom.xml"):
        fail("root reactor does not include cloudmold-module-crm")

    server_dependencies = active_dependency_artifacts(REPO_ROOT / "yudao-server/pom.xml")
    if "cloudmold-module-crm-server" not in server_dependencies:
        fail("yudao-server does not activate cloudmold-module-crm-server")
    if "yudao-module-crm-server" in server_dependencies:
        fail("legacy yudao-module-crm-server is active in yudao-server")

    backend_sources = source_files(CRM_ROOT, (".java", ".xml"))
    reject_tokens(
        backend_sources,
        {
            "cn.iocoder.yudao.module.crm": "legacy CRM Java package is a runtime dependency",
            "crm_product": "legacy CRM product table is referenced",
            "crm_receivable": "legacy CRM receivable table is referenced",
            "crm_receivable_plan": "legacy CRM receivable-plan table is referenced",
        },
    )

    frontend_sources: list[Path] = []
    for root in FRONTEND_CRM_ROOTS:
        frontend_sources.extend(source_files(root, (".ts", ".tsx", ".vue")))
    reject_tokens(
        frontend_sources,
        {
            "#/api/crm": "legacy CRM API client is imported",
            "#/views/crm": "legacy CRM page is imported",
            "/crm/product": "legacy CRM product endpoint is referenced",
            "/crm/receivable": "legacy CRM receivable endpoint is referenced",
        },
    )

    print("cloudmold CRM boundary checks passed")


if __name__ == "__main__":
    main()
