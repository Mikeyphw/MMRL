#!/usr/bin/env python3
"""Source-bound MMRL R36 OfficialDebug native DAG admission (no local Gradle)."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

TARGET = "mmrl_hosted_r36"
WORKFLOW = "ci"
DEVTOOL_SHA = "246f168ebdef6d9b25c194268c810c877d099fa3"
PROFILES = "ubuntu-24.04-x64"
TASKS = [":app:lintOfficialDebug", ":app:assembleOfficialDebug"]
OUTPUT = "app/build/outputs/apk/official/debug/MMRL-R36-OfficialDebug.apk"
PACKAGE = "com.mikeyphw.mmrl.debug"


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def checked_graph(root: Path) -> dict:
    from devtool.actions_graph_export import compile_graph
    graph = compile_graph(root, target=TARGET, workflow=WORKFLOW,
                          devtool_sha=DEVTOOL_SHA, host_profiles=PROFILES)
    nodes = graph["jobs"]
    if len(nodes) != 1 or nodes[0]["id"] != "node-01":
        raise ValueError("MMRL R36 needs exactly one reviewed native DAG node")
    node = nodes[0]
    review = node.get("android_review") or {}
    if (node["provider"] != "android-gradle"
            or review.get("semantic_request") is not True
            or review.get("project_dir") != ""
            or review.get("variant") != "officialDebug"
            or review.get("wrapper_version") != "9.7.1"
            or review.get("java_version") != "21"
            or review.get("tasks") != TASKS
            or node["outputs"] != [OUTPUT]
            or review.get("output_role") != "local-bytes-only"
            or review.get("signer_authority") is not False
            or review.get("release_qualified") is not False
            or review.get("device_qualified") is not False):
        raise ValueError("MMRL R36 native debug-only source/output contract changed")
    gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
    if (not re.search(r'if\s*\(r36HostedDebug\s*&&\s*variant.name\s*==\s*"officialDebug"\)', gradle)
            or 'output.outputFileName.set("MMRL-R36-OfficialDebug.apk")' not in gradle
            or 'providers.gradleProperty("mmrlR36HostedDebug")' not in gradle
            or 'releaseSigningProperties = project.releaseSigningProperties()' not in gradle):
        raise ValueError("MMRL R36 opt-in debug output mapping or release boundary changed")
    return graph


def check_runner(root: Path) -> tuple[str, int, int]:
    if (os.environ.get("GITHUB_ACTIONS") != "true"
            or os.environ.get("RUNNER_OS") != "Linux"
            or os.environ.get("RUNNER_ARCH") != "X64"
            or os.environ.get("GITHUB_REPOSITORY") != "Mikeyphw/MMRL"
            or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"
            or os.environ.get("ORG_GRADLE_PROJECT_mmrlR36HostedDebug") != "true"
            or git(root, "rev-parse", "HEAD") != os.environ.get("GITHUB_SHA")):
        raise ValueError("MMRL R36 requires matching exact GitHub Linux run with approved debug output")
    try:
        run, attempt = int(os.environ["GITHUB_RUN_ID"]), int(os.environ["GITHUB_RUN_ATTEMPT"])
    except (ValueError, KeyError) as exc:
        raise ValueError("MMRL R36 missing GitHub run identity") from exc
    if run <= 0 or attempt <= 0:
        raise ValueError("MMRL R36 requires positive run/attempt")
    return os.environ["GITHUB_SHA"], run, attempt


def write_exclusive(path: Path, value: dict) -> None:
    if path.is_symlink() or path.exists():
        raise ValueError("MMRL R36 refuses to replace existing evidence")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as f:
        json.dump(value, f, sort_keys=True, indent=2)
        f.write("\n")


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
        plan = {"schema": "mmrl.r36.hosted-plan.v1",
                "status": "REVIEWED_OFFICIAL_DEBUG_ONLY", "source_sha": source,
                "devtool_sha": DEVTOOL_SHA, "graph_sha256": graph["graph_sha256"],
                "tasks": TASKS, "output": OUTPUT, "package": PACKAGE,
                "release_qualified": False, "device_qualified": False,
                "artifact_independently_authenticated": False}
        if args.mode == "plan":
            if args.evidence:
                write_exclusive(Path(args.evidence), plan)
            print(json.dumps(plan, sort_keys=True))
            return 0
        source, run, attempt = check_runner(root)
        if not args.outputs or not args.inputs:
            raise ValueError("MMRL R36 hosted run requires inputs and outputs")
        from devtool.actions_graph_export import run_node
        if args.mode == "run":
            result = run_node(root, target=TARGET, workflow=WORKFLOW,
                              devtool_sha=DEVTOOL_SHA, graph_sha=graph["graph_sha256"],
                              job_id="node-01", inputs=Path(args.inputs),
                              outputs=Path(args.outputs), host_profiles=PROFILES)
            print(json.dumps(result, sort_keys=True))
            return 0
        # Read-only independent verification of the staged APK & producer receipt.
        staging = Path(args.outputs) / "node-01"
        staged_apk = staging / "payload" / OUTPUT
        if staged_apk.is_symlink() or not staged_apk.is_file() or staged_apk.stat().st_size == 0:
            raise ValueError("MMRL R36 staged exact APK absent or unsafe")
        sdk = Path(os.environ.get("ANDROID_HOME", "/nonexistent")) / "build-tools" / "36.0.0"
        check = subprocess.run([str(sdk / "apksigner"), "verify", "--print-certs", str(staged_apk)],
                               capture_output=True, text=True, timeout=45, check=True)
        cert = re.findall(r"(?im)^Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$", check.stdout)
        if len(cert) != 1:
            raise ValueError("MMRL R36 invalid debug signer fingerprint")
        badging = subprocess.run([str(sdk / "aapt"), "dump", "badging", str(staged_apk)],
                                 capture_output=True, text=True, timeout=45, check=True)
        if re.search(r"(?m)^package: name='" + re.escape(PACKAGE) + r"'\s", badging.stdout) is None:
            raise ValueError("MMRL R36 package identity mismatch")
        receipt_path = staging / "receipt.json"
        if receipt_path.is_symlink() or not receipt_path.is_file() or receipt_path.stat().st_size > 32768:
            raise ValueError("MMRL R36 missing native DAG handoff receipt")
        receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
        if (receipt.get("schema_version") != 2
                or receipt.get("kind") != "devtool.actions.node-handoff"
                or receipt.get("source_commit") != source
                or receipt.get("graph_sha256") != graph["graph_sha256"]
                or receipt.get("run_id") != str(run)
                or receipt.get("attempt") != attempt
                or receipt.get("authenticated") is not False
                or receipt.get("release_qualified") is not False):
            raise ValueError("MMRL R36 native DAG receipt lineage mismatch")
        data = staged_apk.read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        files = receipt.get("files")
        if (not isinstance(files, list) or len(files) != 1
                or files[0].get("path") != OUTPUT
                or files[0].get("size") != len(data)
                or files[0].get("sha256") != digest):
            raise ValueError("MMRL R36 staged payload/receipt digest mismatch")
        evidence = {**plan, "schema": "mmrl.r36.hosted-verification.v1",
                    "status": "VERIFIED_DEBUG_NON_RELEASE", "run_id": run, "attempt": attempt,
                    "apk_sha256": digest, "apk_size": len(data),
                    "signer_sha256": cert[0].lower(), "signature_independently_verified": True,
                    "package_independently_verified": True}
        if not args.evidence:
            raise ValueError("MMRL R36 verification requires distinct evidence output")
        write_exclusive(Path(args.evidence), evidence)
        print(json.dumps(evidence, sort_keys=True))
        return 0
    except (ValueError, OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired, KeyError) as exc:
        print("MMRL_R36_REFUSED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
