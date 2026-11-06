#!/usr/bin/env python3
"""Source-pinned native Devtool Android DAG for rebuilt MMRL (GitHub only).

Review/execute via Devtool's real hosted android-gradle provider; this helper
never substitutes a shell Gradle build and never signs/releases an APK.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

REPO = "Mikeyphw/MMRL"
TARGET = "mmrl_next"
WORKFLOW = "ci"
DEVTOOL_SHA = "bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c"
PROFILE = "ubuntu-24.04-x64"
APK_TASK = [":app:assembleOfficialDebug"]
LINT_TASK = [":app:lintOfficialDebug"]
TEST_TASK = [":app:testOfficialDebugUnitTest"]
OUTPUT = "app/build/outputs/apk/official/debug/MMRL-Next-CI-OfficialDebug.apk"
PACKAGE = "com.dergoogler.mmrl.debug"


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def hosted_identity(root: Path) -> tuple[str, int, int]:
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_OS") != "Linux"
            or os.environ.get("RUNNER_ARCH") != "X64"
            or os.environ.get("GITHUB_REPOSITORY") != REPO
            or os.environ.get("GITHUB_EVENT_NAME") not in {"push", "pull_request"}
            or os.environ.get("MMRL_NEXT_DEVTOOL") != "1"
            or git(root, "rev-parse", "HEAD") != os.environ.get("GITHUB_SHA")):
        raise ValueError("Exact-source MMRL GitHub-hosted Linux run required")
    run, attempt = int(os.environ["GITHUB_RUN_ID"]), int(os.environ["GITHUB_RUN_ATTEMPT"])
    if run < 1 or attempt < 1:
        raise ValueError("Invalid GitHub run/attempt identity")
    return os.environ["GITHUB_SHA"], run, attempt


def checked_graph(root: Path) -> dict:
    from devtool.actions_graph_export import compile_graph

    graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, host_profiles=PROFILE)
    nodes = graph["jobs"]
    if [node.get("id") for node in nodes] != ["node-01", "node-02", "node-03"]:
        raise ValueError("Expected three native Devtool Android DAG nodes")
    for node, name, tasks, outputs in (
        (nodes[0], "assemble_debug", APK_TASK, [OUTPUT]),
        (nodes[1], "lint_debug", LINT_TASK, []),
        (nodes[2], "unit_debug", TEST_TASK, []),
    ):
        review = node.get("android_review") or {}
        if (node.get("provider") != "android-gradle"
                or node.get("job") != name
                or node.get("needs") != []
                or review.get("semantic_request") is not True
                or review.get("project_dir") != ""
                or review.get("variant") != "officialDebug"
                or review.get("wrapper_version") != "9.1.0"
                or review.get("java_version") != "21"
                or review.get("tasks") != tasks
                or node.get("outputs") != outputs
                or review.get("output_role") != "local-bytes-only"
                or review.get("signer_authority") is not False
                or review.get("release_qualified") is not False
                or review.get("device_qualified") is not False):
            raise ValueError("Native Devtool source graph differs from N03 contract: " + name)
    return graph


def write_once(path: Path, document: dict) -> None:
    if path.is_symlink() or path.exists():
        raise ValueError("Refusing to overwrite existing evidence: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as f:
        json.dump(document, f, indent=2, sort_keys=True)
        f.write("\n")


def verify_apk(root: Path, graph: dict, outputs: Path, evidence: Path, plan: dict) -> dict:
    source, run, attempt = hosted_identity(root)
    stage = outputs / "node-01"
    apk = stage / "payload" / OUTPUT
    receipt_path = stage / "receipt.json"
    if apk.is_symlink() or not apk.is_file() or apk.stat().st_size <= 0:
        raise ValueError("Devtool APK output missing or invalid")
    if receipt_path.is_symlink() or not receipt_path.is_file() or receipt_path.stat().st_size > 32768:
        raise ValueError("Devtool native node receipt missing or invalid")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if (receipt.get("schema_version") != 2
            or receipt.get("kind") != "devtool.actions.node-handoff"
            or receipt.get("source_commit") != source
            or receipt.get("graph_sha256") != graph["graph_sha256"]
            or receipt.get("run_id") != str(run)
            or receipt.get("attempt") != attempt
            or receipt.get("release_qualified") is not False):
        raise ValueError("Native Devtool receipt has incorrect source or run identity")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    files = receipt.get("files")
    if (not isinstance(files, list) or len(files) != 1
            or files[0].get("path") != OUTPUT
            or files[0].get("sha256") != digest
            or files[0].get("size") != apk.stat().st_size):
        raise ValueError("Native Devtool APK receipt differs from published output")
    sdk = Path(os.environ["ANDROID_SDK_ROOT"]) / "build-tools" / "36.0.0"
    certs_text = subprocess.run([str(sdk / "apksigner"), "verify", "--print-certs", str(apk)],
                                capture_output=True, text=True, timeout=45, check=True).stdout
    certs = re.findall(r"(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", certs_text)
    if len(certs) != 1:
        raise ValueError("Expected one verified APK certificate")
    badge = subprocess.run([str(sdk / "aapt"), "dump", "badging", str(apk)],
                           capture_output=True, text=True, timeout=45, check=True).stdout
    if not re.search(r"(?m)^package: name='" + re.escape(PACKAGE) + r"'\s", badge):
        raise ValueError("APK debug application ID does not match ordinary MMRL debug package")
    result = {**plan, "status": "VERIFIED_DEBUG_ONLY", "run_id": run, "attempt": attempt,
              "apk_sha256": digest, "apk_size": apk.stat().st_size,
              "debug_signer_sha256": certs[0].lower(), "package_verified": True,
              "signature_verified": True, "device_qualified": False,
              "release_qualified": False}
    write_once(evidence, result)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "run", "verify"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--node", choices=("apk", "lint", "test"))
    parser.add_argument("--inputs")
    parser.add_argument("--outputs")
    parser.add_argument("--evidence")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    try:
        graph = checked_graph(root)
        source = git(root, "rev-parse", "HEAD")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            hosted_identity(root)
        plan = {"schema": "mmrl.next.n03.devtool-plan.v1",
                "source_sha": source, "devtool_sha": DEVTOOL_SHA,
                "graph_sha256": graph["graph_sha256"],
                "node_ids": {"apk": "node-01", "lint": "node-02", "test": "node-03"},
                "package": PACKAGE, "output": OUTPUT,
                "release_qualified": False, "device_qualified": False}
        if args.mode == "plan":
            if args.evidence:
                write_once(Path(args.evidence), plan)
            print(json.dumps(plan, sort_keys=True))
            return 0
        if not args.inputs or not args.outputs:
            raise ValueError("--inputs and --outputs required for node execution")
        if args.mode == "run":
            if not args.node:
                raise ValueError("--node required for run")
            hosted_identity(root)
            from devtool.actions_graph_export import run_node
            result = run_node(root, target=TARGET, workflow=WORKFLOW,
                              devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"],
                              job_id=plan["node_ids"][args.node], inputs=Path(args.inputs),
                              outputs=Path(args.outputs), host_profiles=PROFILE)
            print(json.dumps(result, sort_keys=True))
            return 0
        if not args.evidence:
            raise ValueError("--evidence required for verify")
        print(json.dumps(verify_apk(root, graph, Path(args.outputs),
                                    Path(args.evidence), plan), sort_keys=True))
        return 0
    except (ValueError, KeyError, OSError, RuntimeError, subprocess.CalledProcessError,
            subprocess.TimeoutExpired) as exc:
        print("N03_DEVTOOL_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
