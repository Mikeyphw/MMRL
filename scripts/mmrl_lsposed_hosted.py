#!/usr/bin/env python3
"""Source-bound Devtool native Android DAG admission for upstream-based MMRL.

Only debug artifacts are admitted. Not a release, signing, or device authority.
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

TARGET = "mmrl_lsposed_ci"
WORKFLOW = "ci"
DEVTOOL_SHA = "246f168ebdef6d9b25c194268c810c877d099fa3"
PROFILES = "ubuntu-24.04-x64"
TASKS = [":app:lintOfficialDebug", ":app:assembleOfficialDebug"]
OUTPUT = "app/build/outputs/apk/official/debug/MMRL-LSPosed-CI-OfficialDebug.apk"
PACKAGE = "com.dergoogler.mmrl.debug"  # Phase 01 retains upstream's identity.


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def checked_graph(root: Path) -> dict:
    from devtool.actions_graph_export import compile_graph

    graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, host_profiles=PROFILES)
    nodes = graph["jobs"]
    if len(nodes) != 1 or nodes[0]["id"] != "node-01":
        raise ValueError("Only the single reviewed OfficialDebug DAG node is accepted")
    node = nodes[0]
    review = node.get("android_review") or {}
    if (node["provider"] != "android-gradle"
            or review.get("semantic_request") is not True
            or review.get("project_dir") != ""
            or review.get("variant") != "officialDebug"
            or review.get("wrapper_version") != "9.1.0"
            or review.get("java_version") != "21"
            or review.get("tasks") != TASKS
            or node["outputs"] != [OUTPUT]
            or review.get("output_role") != "local-bytes-only"
            or review.get("signer_authority") is not False
            or review.get("release_qualified") is not False
            or review.get("device_qualified") is not False):
        raise ValueError("Native Android review did not match the upstream-only debug contract")
    source = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    if ("providers.gradleProperty(\"mmrlLsposedHosted\")" not in source
            or "MMRL-LSPosed-CI-OfficialDebug.apk" not in source
            or 'variant.name == "officialDebug"' not in source):
        raise ValueError("Opt-in named OfficialDebug APK mapping missing")
    return graph


def check_runner(root: Path) -> tuple[str, int, int]:
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_OS") != "Linux"
            or os.environ.get("RUNNER_ARCH") != "X64"
            or os.environ.get("GITHUB_REPOSITORY") != "Mikeyphw/MMRL"
            or os.environ.get("GITHUB_EVENT_NAME") not in ("push", "pull_request")
            or os.environ.get("ORG_GRADLE_PROJECT_mmrlLsposedHosted") != "true"
            or git(root, "rev-parse", "HEAD") != os.environ.get("GITHUB_SHA")):
        raise ValueError("Expected an exact-source Linux hosted push/PR run")
    try:
        run = int(os.environ["GITHUB_RUN_ID"])
        attempt = int(os.environ["GITHUB_RUN_ATTEMPT"])
    except (ValueError, KeyError) as exc:
        raise ValueError("Invalid GitHub run identity") from exc
    if run < 1 or attempt < 1:
        raise ValueError("Invalid GitHub run/attempt")
    return os.environ["GITHUB_SHA"], run, attempt


def write_once(path: Path, payload: dict) -> None:
    if path.is_symlink() or path.exists():
        raise ValueError("Refusing to replace an existing evidence file: " + str(path))
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(payload, stream, sort_keys=True, indent=2)
        stream.write("\n")


def verify_apk(root: Path, graph: dict, outputs: Path, evidence_path: Path, source: str,
               run: int, attempt: int, plan: dict) -> dict:
    from pathlib import Path
    staging = outputs / "node-01"
    apk = staging / "payload" / OUTPUT
    if apk.is_symlink() or not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("Expected exact APK missing from the Devtool native handoff")
    sdk = Path(os.environ.get("ANDROID_HOME", "/missing")) / "build-tools" / "36.0.0"
    sig = subprocess.run([str(sdk / "apksigner"), "verify", "--print-certs", str(apk)],
                         capture_output=True, text=True, timeout=60, check=True)
    certs = re.findall(r"(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$", sig.stdout)
    if len(certs) != 1:
        raise ValueError("APK must have exactly one verified signer certificate")
    badging = subprocess.run([str(sdk / "aapt"), "dump", "badging", str(apk)],
                             capture_output=True, text=True, timeout=60, check=True)
    if not re.search(r"(?m)^package: name='" + re.escape(PACKAGE) + r"'\s", badging.stdout):
        raise ValueError("APK package differs from the selected upstream debug identity")
    receipt_path = staging / "receipt.json"
    if receipt_path.is_symlink() or not receipt_path.is_file() or receipt_path.stat().st_size > 32768:
        raise ValueError("Missing or unsafe native node receipt")
    receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
    if (receipt.get("schema_version") != 2
            or receipt.get("kind") != "devtool.actions.node-handoff"
            or receipt.get("source_commit") != source
            or receipt.get("graph_sha256") != graph["graph_sha256"]
            or receipt.get("run_id") != str(run)
            or receipt.get("attempt") != attempt
            or receipt.get("authenticated") is not False
            or receipt.get("release_qualified") is not False):
        raise ValueError("Native handoff provenance does not match the exact run")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    entries = receipt.get("files")
    if (not isinstance(entries, list) or len(entries) != 1
            or entries[0].get("path") != OUTPUT
            or entries[0].get("sha256") != digest
            or entries[0].get("size") != apk.stat().st_size):
        raise ValueError("Produced APK does not match the Devtool native receipt")
    verified = {
        **plan, "schema": "mmrl.lsposed.phase01.debug-verification.v1",
        "status": "VERIFIED_DEBUG_NOT_RELEASE", "run_id": run, "attempt": attempt,
        "apk_sha256": digest, "apk_size": apk.stat().st_size,
        "signer_sha256": certs[0].lower(), "signature_verified": True,
        "package_verified": True, "device_tests_executed": False,
    }
    write_once(evidence_path, verified)
    return verified


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("plan", "run", "verify"))
    parser.add_argument("--root", default=".")
    parser.add_argument("--inputs")
    parser.add_argument("--outputs")
    parser.add_argument("--evidence")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        graph = checked_graph(root)
        source = git(root, "rev-parse", "HEAD")
        if os.environ.get("GITHUB_ACTIONS") == "true":
            check_runner(root)
        plan = {
            "schema": "mmrl.lsposed.phase01.plan.v1",
            "status": "REVIEWED_UPSTREAM_OFFICIAL_DEBUG", "source_sha": source,
            "devtool_sha": DEVTOOL_SHA, "graph_sha256": graph["graph_sha256"],
            "tasks": TASKS, "output": OUTPUT, "package": PACKAGE,
            "release_qualified": False, "device_qualified": False,
            "artifact_independently_authenticated": False,
        }
        if args.mode == "plan":
            if args.evidence:
                write_once(Path(args.evidence), plan)
            print(json.dumps(plan, sort_keys=True))
            return 0
        source, run, attempt = check_runner(root)
        if not args.inputs or not args.outputs:
            raise ValueError("Both native input and output handoff paths are required")
        outputs = Path(args.outputs)
        inputs = Path(args.inputs)
        if args.mode == "run":
            from devtool.actions_graph_export import run_node
            result = run_node(root, target=TARGET, workflow=WORKFLOW,
                              devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"],
                              job_id="node-01", inputs=inputs, outputs=outputs,
                              host_profiles=PROFILES)
            print(json.dumps(result, sort_keys=True))
            return 0
        if not args.evidence:
            raise ValueError("Verification evidence path required")
        verified = verify_apk(root, graph, outputs, Path(args.evidence), source,
                              run, attempt, plan)
        print(json.dumps(verified, sort_keys=True))
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired,
            KeyError, RuntimeError) as exc:
        print("MMRL_LSPOSED_CI_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
