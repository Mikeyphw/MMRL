#!/usr/bin/env python3
"""Targeted qualification for MMRL-DT-X02 Android validation/release authority.

This deliberately proves structure/planning without running the expensive Gradle,
device, signing, or release workload. MMRL-DT-G2 owns the authoritative runtime gate.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TARGET = "mmrl_android"


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def run(argv: list[str], *, env: dict[str, str], expect_success: bool = True) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(
        argv,
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if expect_success and proc.returncode != 0:
        raise ValidationError(
            f"command failed ({proc.returncode}): {' '.join(argv)}\n"
            f"stdout:\n{proc.stdout}\nstderr:\n{proc.stderr}"
        )
    return proc


def run_json(argv: list[str], *, env: dict[str, str]) -> dict[str, Any]:
    proc = run(argv, env=env)
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise ValidationError(f"command did not emit JSON: {' '.join(argv)}\n{proc.stdout}\n{proc.stderr}") from exc


def base(exe: str) -> list[str]:
    return [exe, "--repo", str(ROOT), "--target", TARGET]


def validate_repository_files(env: dict[str, str]) -> None:
    run([sys.executable, "scripts/validate-mmrl-source-hygiene.py"], env=env)
    run(
        [
            "bash", "-n",
            "build-release-apk.sh",
            "scripts/run-mmrl-release-seal.sh",
            "scripts/run-mmrl-device-validation.sh",
        ],
        env=env,
    )
    run(
        [
            sys.executable, "-m", "py_compile",
            "scripts/validate-mmrl-source-hygiene.py",
            "scripts/validate-mmrl-devtool-foundation-gate.py",
            "scripts/validate-mmrl-devtool-android-release-authority.py",
        ],
        env=env,
    )


def validate_config(exe: str, env: dict[str, str]) -> None:
    migrated = run_json(base(exe) + ["config", "migrate", "--check", "--json"], env=env)
    require(migrated.get("ok") is True, f"config migration check failed: {migrated}")
    require(migrated.get("data", {}).get("needs_migration") is False, "Devtool config is not canonical")

    cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    target = cfg["targets"][TARGET]
    gradle = target["gradle"]
    android = target["android"]
    validation = cfg["validation"]

    require(gradle.get("provider") == "wrapper", f"Gradle provider drifted: {gradle}")
    require(gradle.get("max_workers") == 1, f"Gradle worker policy drifted: {gradle}")
    require(gradle.get("heap_mb") == 768, f"native Android Gradle heap is not bounded to 768 MiB: {gradle}")
    require(gradle.get("validation_packaging_heap_mb") == 768, f"packaging heap may escape MMRL's 768 MiB envelope: {gradle}")
    require(gradle.get("configuration_cache") == "off", f"Configuration Cache policy drifted: {gradle}")
    require(gradle.get("parallel") is False and gradle.get("no_daemon") is True, f"Termux Gradle safety drifted: {gradle}")
    require(validation.get("android_safe") is True and validation.get("split_gradle") is True, f"Android validation safety drifted: {validation}")

    adb = android.get("adb", {})
    instr = android.get("instrumentation", {})
    signing = android.get("signing", {}).get("release", {})
    release = android.get("release", {})
    require(adb.get("provider") == "auto" and adb.get("device_policy") == "exactly-one", f"ADB authority drifted: {adb}")
    require(instr.get("policy") == "required" and instr.get("variant") == "officialDebug", f"instrumentation authority drifted: {instr}")
    require(instr.get("capture_logcat_on_failure") is True and instr.get("capture_screenshot_on_failure") is True, f"failure evidence policy drifted: {instr}")
    require(signing.get("mode") == "project" and signing.get("policy") == "release-required", f"project signing authority drifted: {signing}")
    require(release.get("variant") == "officialRelease", f"release variant drifted: {release}")
    for key in ("unit_tests", "lint", "install_smoke", "upgrade_smoke", "verify_signature", "verify_certificate_identity"):
        require(release.get(key) is True, f"release gate {key} is not enabled: {release}")
    require(release.get("device_tests") == "when-device-present", f"release instrumentation policy drifted: {release}")
    require(release.get("package") == "com.mikeyphw.mmrl", f"release package drifted: {release}")

    tasks = target["tasks"]
    require(tasks.get("release_lint") == [":app:fullLintOfficialRelease"], f"release lint lane drifted: {tasks.get('release_lint')}")
    require(tasks.get("release_build") == [":app:assembleOfficialRelease"], f"release build lane drifted: {tasks.get('release_build')}")
    require(":app:testOfficialReleaseUnitTest" in tasks.get("release_test", []), f"release unit lane drifted: {tasks.get('release_test')}")
    require(tasks.get("instrumentation") == [":app:connectedOfficialDebugAndroidTest"], f"instrumentation task identity drifted: {tasks.get('instrumentation')}")

    jobs = target["jobs"]
    require("device-compat" not in jobs, "obsolete X01 device compatibility job remains")
    device = jobs.get("device-native", {})
    require(device.get("runner") == "android" and device.get("action") == "instrumentation", f"native device job drifted: {device}")
    metadata = device.get("metadata", {})
    require(metadata.get("resource_class") == "device-exclusive", f"device resource class drifted: {metadata}")
    require("mmrl-adb-device" in metadata.get("global_reservations", []), f"device reservation missing: {metadata}")

    workflows = target["workflows"]
    require("release-seal" not in workflows and "release-check" in workflows, f"pre-release workflow naming drifted: {workflows.keys()}")
    require(workflows["device"] == [{"id": "device-native", "ref": "job:device-native", "depends_on": []}], f"device workflow drifted: {workflows['device']}")


def validate_wrapper(exe: str, env: dict[str, str]) -> None:
    verify = run_json(base(exe) + ["wrapper", "verify", "--json"], env=env)
    require(verify.get("ok") is True and verify.get("exit_code") == 0, f"wrapper verify failed: {verify}")
    seal = run_json(base(exe) + ["wrapper", "seal", "--json"], env=env)
    require(seal.get("ok") is True and seal.get("data", {}).get("ok") is True, f"wrapper seal failed: {seal}")

    session = run_json(base(exe) + ["wrapper", "resolve", "--json", "validate-session", "--complete"], env=env)["data"]
    argv = session["canonical_argv"]
    require(session.get("execution_kind") == "native" and session.get("devtool_command") == "validate", f"validate-session does not resolve natively: {session}")
    for token in ("--sequential", "--complete", "--failure-action", "pause"):
        require(token in argv, f"validate-session omitted {token}: {argv}")

    retry = run_json(base(exe) + ["wrapper", "resolve", "--json", "validate-session", "--retry-failed", "--complete"], env=env)["data"]
    require("--retry-failed" in retry["canonical_argv"], f"validate-session retry binding drifted: {retry}")

    device = run_json(base(exe) + ["wrapper", "resolve", "--json", "device"], env=env)["data"]
    require(device.get("execution_kind") == "workflow" and device.get("workflow_action") == "device", f"device wrapper drifted: {device}")


def validate_device_plan(exe: str, env: dict[str, str]) -> None:
    payload = run_json(base(exe) + ["plan", "device", "--json"], env=env)
    require(payload.get("ok") is True, f"device plan failed: {payload}")
    data = payload["data"]
    require(data["summary"]["node_count"] == 1, f"device plan should have one EXO node: {data['summary']}")
    schedule = data["schedule"]
    require(not schedule.get("notes"), f"device plan scheduler notes indicate unresolved scope: {schedule.get('notes')}")
    node = schedule["nodes"][0]
    require(node.get("target_local_job") is True, f"device job is not target-local: {node}")
    require(node.get("runner") == "android" and node.get("action") == "instrumentation", f"device node does not use native Android instrumentation: {node}")
    require(node.get("writes_known") is True, f"device write scope is unknown: {node}")
    require(node.get("resource_class") == "device-exclusive", f"device resource class drifted: {node}")
    require("mmrl-adb-device" in node.get("global_reservations", []), f"device reservation missing: {node}")

    commands = data["commands"]
    require([item["name"] for item in commands] == ["instrumentation-build", "instrumentation"], f"unexpected device command lowering: {commands}")
    build = commands[0]["argv"]
    instrument = commands[1]["argv"]
    for token in (":app:assembleOfficialDebug", ":app:assembleOfficialDebugAndroidTest", "--gradle-jvm-heap-mb", "768"):
        require(token in build, f"device build plan omitted {token}: {build}")
    for token in ("devtool.android_instrumentation", "--canonical", "--skip-build", "--policy", "required", "--device-policy", "exactly-one"):
        require(token in instrument, f"canonical instrumentation plan omitted {token}: {instrument}")


def validate_release_check_plan(exe: str, env: dict[str, str]) -> None:
    payload = run_json(base(exe) + ["plan", "release-check", "--json"], env=env)
    require(payload.get("ok") is True, f"release-check plan failed: {payload}")
    data = payload["data"]
    require(data["summary"]["node_count"] == 7, f"release-check DAG drifted: {data['summary']}")
    nodes = {node["job_local_name"]: node for node in data["schedule"]["nodes"]}
    expected = {"source-hygiene", "static-gates", "unit-contracts", "compile-contracts", "strict-lint", "package-seal", "device-native"}
    require(set(nodes) == expected, f"release-check job set drifted: {sorted(nodes)}")
    require(all(node.get("writes_known") is True for node in nodes.values()), "release-check contains unknown write scope")
    require(nodes["source-hygiene"].get("read_only") is True, "source-hygiene lost read-only classification")
    require(nodes["device-native"].get("resource_class") == "device-exclusive", "release-check device boundary lost exclusive classification")


def validate_release_planner() -> None:
    # Use the exact Devtool module installed alongside the executable running this
    # validation. Planning is side-effect free: it proves the AOPS04 lanes without
    # running Gradle, signing, device work, or artifact publication.
    from devtool.android.release import AndroidReleaseOrchestrator, release_options
    from devtool.config import load_project_config

    cfg = load_project_config(str(ROOT), None, target_name=TARGET)
    opts = release_options(cfg)
    require(opts.variant == "officialRelease", f"AOPS04 selected wrong release variant: {opts}")
    require(opts.device_tests == "when-device-present", f"AOPS04 device-test policy drifted: {opts}")
    require(opts.install_smoke and opts.upgrade_smoke, f"AOPS04 smoke policy drifted: {opts}")
    require(opts.verify_signature and opts.verify_certificate_identity, f"AOPS04 signature policy drifted: {opts}")

    _cfg, _opts, phases = AndroidReleaseOrchestrator(cfg).plan()
    require([name for name, _ in phases] == ["unit-tests", "lint", "package", "instrumentation"], f"AOPS04 phase graph drifted: {[name for name, _ in phases]}")
    expected_tasks = {
        "unit-tests": {"verifyStableToolchainBaseline", "verifyMmrlProductBoundary", "verifyRepositoryHygiene", ":platform:testNativeContracts", ":app:testOfficialReleaseUnitTest"},
        "lint": {":app:fullLintOfficialRelease"},
        "package": {":app:assembleOfficialRelease"},
        "instrumentation": {":app:assembleOfficialDebug", ":app:assembleOfficialDebugAndroidTest"},
    }
    for phase, specs in phases:
        seen: set[str] = set()
        for spec in specs:
            metadata = spec.metadata or {}
            seen.update(metadata.get("gradle_tasks") or [])
            plan = metadata.get("gradle_execution_plan") or {}
            policy = plan.get("policy") or {}
            heap = policy.get("heap") or {}
            if heap:
                require(heap.get("effective_mb") == 768, f"{phase} escaped the 768 MiB heap envelope: {heap}")
        require(expected_tasks[phase].issubset(seen), f"AOPS04 {phase} tasks drifted: expected {sorted(expected_tasks[phase])}, saw {sorted(seen)}")


def validate_frontends() -> None:
    release = (ROOT / "scripts/run-mmrl-release-seal.sh").read_text(encoding="utf-8")
    device = (ROOT / "scripts/run-mmrl-device-validation.sh").read_text(encoding="utf-8")
    build_release = (ROOT / "build-release-apk.sh").read_text(encoding="utf-8")

    require("--target mmrl_android release" in release, "release frontend does not enter canonical AOPS04")
    require("gradlew" not in release and "devtoolw release" not in release, "release frontend retains a competing execution path")
    require("--target mmrl_android instrumentation" in device, "device frontend does not enter canonical instrumentation")
    for forbidden in ("adb devices", "gradlew", "connectedOfficialDebugAndroidTest", "su -c id"):
        require(forbidden not in device, f"device frontend retained obsolete logic: {forbidden}")
    require("scripts/run-mmrl-release-seal.sh" in build_release, "release build frontend bypasses release authority")
    require("--no-install-smoke" in build_release and "--no-upgrade-smoke" in build_release, "package-only release mode does not explicitly disable device smoke")


def main() -> int:
    exe = os.environ.get("DEVTOOL_X02_EXECUTABLE") or shutil.which("devtool")
    require(bool(exe), "Devtool executable is unavailable")
    assert exe is not None

    with tempfile.TemporaryDirectory(prefix="mmrl-dt-x02-") as tmp:
        env = os.environ.copy()
        env["DEVTOOL_GLOBAL_CONFIG"] = str(Path(tmp) / "nonexistent-global.toml")
        env.setdefault("PYTHONDONTWRITEBYTECODE", "1")

        validate_repository_files(env)
        validate_config(exe, env)
        validate_wrapper(exe, env)
        validate_device_plan(exe, env)
        validate_release_check_plan(exe, env)
        validate_release_planner()
        validate_frontends()

    print("MMRL-DT-X02 Android validation/release authority: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as exc:
        print(f"MMRL-DT-X02 Android validation/release authority: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
