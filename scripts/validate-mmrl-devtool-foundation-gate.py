#!/usr/bin/env python3
"""MMRL-DT-G1 canonical Devtool foundation qualification gate.

This gate is intentionally narrow: it qualifies the execution foundation laid by
MMRL-DT-X01 without running the Android/Gradle release workload owned by X02/G2.
It validates canonical configuration, wrapper resolution, EXO/TLJ ownership,
workflow DAG/scheduler truth, native-Termux preflight/tool truth, and raw-Gradle ingress
classification.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TARGET = "mmrl_android"
EXPECTED_COMMANDS = {"build", "test", "lint", "validate", "clean", "package", "release", "device"}
EXPECTED_JOBS = {
    "source-hygiene",
    "static-gates",
    "unit-contracts",
    "compile-contracts",
    "strict-lint",
    "package-seal",
    "device-compat",
}
EXPECTED_RELEASE_STEPS = [
    ("source-hygiene", "job:source-hygiene", []),
    ("static-gates", "job:static-gates", ["source-hygiene"]),
    ("unit-contracts", "job:unit-contracts", ["static-gates"]),
    ("compile-contracts", "job:compile-contracts", ["static-gates"]),
    ("strict-lint", "job:strict-lint", ["unit-contracts", "compile-contracts"]),
    ("package-seal", "job:package-seal", ["strict-lint"]),
    ("device-compat", "job:device-compat", ["package-seal"]),
]
EXPECTED_VALIDATE_STEPS = EXPECTED_RELEASE_STEPS[:-1]
EXPECTED_DEVICE_STEPS = [("device-compat", "job:device-compat", [])]

# Deliberate temporary/deferred exceptions. X02 removes the device compatibility
# path after native instrumentation qualification; X03 converges CI and Mise.
ALLOWED_RAW_GRADLE_FILES = {
    Path("scripts/run-mmrl-device-validation.sh"),
    Path(".github/workflows/mmrl-release-seal.yml"),
    Path("mise.toml"),
    Path("docs/MMRL_TOOLCHAIN_PRODUCT_BOUNDARY.md"),  # documentation example only
}
SKIP_DIRS = {".git", ".gradle", ".kotlin", "build", ".idea", ".venv", "node_modules"}
TEXT_SUFFIXES = {
    ".sh", ".bash", ".zsh", ".yml", ".yaml", ".toml", ".md", ".txt", ".py", ".kts", ".gradle", ".properties", ".cmd", ".bat"
}
RAW_GRADLE_RE = re.compile(r"(?:^|[\\s'\"`])(\\./gradlew(?:\\.bat)?)(?=$|[\\s'\"`])")


class GateError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


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
        raise GateError(
            f"command failed ({proc.returncode}): {' '.join(argv)}\n"
            f"stdout:\n{proc.stdout}\nstderr:\n{proc.stderr}"
        )
    return proc


def run_json(argv: list[str], *, env: dict[str, str], expect_success: bool = True) -> dict[str, Any]:
    proc = run(argv, env=env, expect_success=expect_success)
    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise GateError(f"command did not emit JSON: {' '.join(argv)}\n{proc.stdout}\n{proc.stderr}") from exc
    payload["__returncode"] = proc.returncode
    payload["__stderr"] = proc.stderr
    return payload


def devtool_base(exe: str) -> list[str]:
    return [exe, "--repo", str(ROOT)]


def validate_repository_scripts(env: dict[str, str]) -> None:
    run([sys.executable, "scripts/validate-mmrl-source-hygiene.py"], env=env)
    run(["bash", "-n", "build-android-app.sh", "build-release-apk.sh", "scripts/run-mmrl-release-seal.sh", "scripts/run-mmrl-device-validation.sh"], env=env)
    run(["sh", "-n", "devtoolw"], env=env)
    run([sys.executable, "-m", "py_compile", "scripts/validate-mmrl-source-hygiene.py", "scripts/validate-mmrl-devtool-foundation-gate.py"], env=env)


def validate_config(exe: str, env: dict[str, str]) -> None:
    migrate = run_json(devtool_base(exe) + ["config", "migrate", "--check", "--json"], env=env)
    require(migrate.get("ok") is True, f"config migrate check failed: {migrate}")
    require(migrate["data"]["needs_migration"] is False, f"config still needs migration: {migrate['data']}")

    cfg = run_json(devtool_base(exe) + ["config", "resolve"], env=env)
    gradle = cfg["project"]["runner_options"]["gradle"]
    validation = cfg["project"]["runner_options"]["validation"]
    require(gradle["provider"] == "wrapper", f"Gradle provider drifted: {gradle}")
    require(gradle["configuration_cache"] == "off", f"Configuration Cache policy drifted: {gradle}")
    require(gradle["telemetry_level"] == "standard", f"Gradle telemetry policy drifted: {gradle}")
    require(gradle["resource_profile"] == "constrained", f"Gradle resource profile drifted: {gradle}")
    require(gradle["max_workers"] == 1 and gradle["parallel"] is False, f"Gradle concurrency policy drifted: {gradle}")
    require(gradle["vfs"] is False and gradle["no_daemon"] is True, f"Gradle Termux safety policy drifted: {gradle}")
    require(gradle["failure_policy"] == "collect", f"Gradle failure policy drifted: {gradle}")
    require(validation["android_safe"] is True, f"Android-safe validation is not enabled: {validation}")
    require(validation["max_workers"] == 1 and validation["parallel"] is False, f"validation concurrency drifted: {validation}")

    source_cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    require(source_cfg.get("toolchain", {}).get("manager") == "none",
            "MMRL canonical Devtool execution must not auto-select mise/asdf/pyenv from convenience markers")
    device_job = source_cfg["targets"][TARGET]["jobs"]["device-compat"]
    require(device_job.get("command") == ["bash", "scripts/run-mmrl-device-validation.sh"],
            f"device compatibility job must enter its non-executable shell script through bash: {device_job.get('command')}")


def validate_wrapper(exe: str, env: dict[str, str]) -> None:
    verify = run_json(devtool_base(exe) + ["wrapper", "verify", "--json"], env=env)
    require(verify.get("ok") is True and verify.get("exit_code") == 0, f"wrapper verify failed: {verify}")

    seal = run_json(devtool_base(exe) + ["wrapper", "seal", "--json"], env=env)
    require(seal.get("ok") is True and seal.get("exit_code") == 0, f"wrapper seal failed: {seal}")
    require(seal["data"].get("ok") is True, f"wrapper seal data failed: {seal['data']}")

    discover = run_json(devtool_base(exe) + ["wrapper", "discover", "--json"], env=env)
    data = discover["data"]
    commands = data["commands"]
    command_names = set(commands if isinstance(commands, dict) else [item["name"] for item in commands])
    require(command_names == EXPECTED_COMMANDS, f"wrapper command surface drifted: {sorted(command_names)}")
    launchers = data["launchers"]
    require(launchers["installed"] is True and launchers["ok"] is True and launchers["drifted"] is False, f"wrapper launcher drift: {launchers}")
    require(data.get("docs") == "docs/WRAPPER.md", f"wrapper docs contract drifted: {data.get('docs')}")

    native_commands = {"build": "build", "test": "test", "lint": "lint", "validate": "validate", "clean": "clean", "package": "package"}
    for name, expected_native in native_commands.items():
        resolved = run_json(devtool_base(exe) + ["wrapper", "resolve", "--json", name], env=env)["data"]
        require(resolved["execution_kind"] == "native", f"wrapper {name} is not native: {resolved}")
        require(resolved["devtool_command"] == expected_native, f"wrapper {name} maps incorrectly: {resolved}")
        require(resolved["scope"]["target"] == TARGET, f"wrapper {name} target drifted: {resolved}")
        require("gradlew" not in " ".join(resolved["canonical_argv"]), f"wrapper {name} bypasses Devtool: {resolved}")

    for name, workflow in {"release": "release-seal", "device": "device"}.items():
        resolved = run_json(devtool_base(exe) + ["wrapper", "resolve", "--json", name], env=env)["data"]
        require(resolved["execution_kind"] == "workflow", f"wrapper {name} is not a workflow: {resolved}")
        require(resolved["workflow_action"] == workflow, f"wrapper {name} workflow drifted: {resolved}")
        argv = resolved["canonical_argv"]
        require("invoke" in argv and workflow in argv, f"wrapper {name} canonical argv drifted: {argv}")
        require(resolved["scope"]["target"] == TARGET, f"wrapper {name} target drifted: {resolved}")


def validate_jobs_and_workflows(exe: str, env: dict[str, str]) -> None:
    tlj = run_json(devtool_base(exe) + ["target-local-jobs", "--target", TARGET, "--json"], env=env)
    require(tlj.get("ok") is True, f"target-local-jobs inspection failed: {tlj}")
    summary = tlj["data"]["summary"]
    require(summary["targets"] == 1, f"unexpected target-local-job target count: {summary}")
    require(summary["target_local_job_count"] == 7, f"unexpected target-local-job count: {summary}")
    require(summary["repository_job_count"] == 0, f"repository-level jobs remain: {summary}")
    require(set(summary["targets_with_target_local_jobs"]) == {TARGET}, f"target-local ownership drifted: {summary}")
    require(set(summary["workflows_using_target_local_jobs"][TARGET]) == {"validate", "device", "release-seal"}, f"TLJ workflow adoption drifted: {summary}")

    workflows = run_json(devtool_base(exe) + ["--target", TARGET, "workflows", "--json"], env=env)
    require(workflows.get("ok") is True, f"workflow inspection failed: {workflows}")
    target_workflows = workflows["data"]["workflows"][TARGET]
    require(set(target_workflows) == {"validate", "device", "release-seal"}, f"workflow set drifted: {sorted(target_workflows)}")

    expected = {
        "validate": EXPECTED_VALIDATE_STEPS,
        "device": EXPECTED_DEVICE_STEPS,
        "release-seal": EXPECTED_RELEASE_STEPS,
    }
    for name, steps_expected in expected.items():
        wf = target_workflows[name]
        actual = [(s["id"], s["ref"], s["depends_on"]) for s in wf["steps"]]
        require(actual == steps_expected, f"workflow {name} DAG drifted: {actual}")
        require(all(s.get("target_local") is True for s in wf["steps"]), f"workflow {name} escaped target-local ownership")
        require(wf.get("schedule_error", "") == "", f"workflow {name} has scheduler error: {wf.get('schedule_error')}")

    plan = run_json(devtool_base(exe) + ["plan", "release-seal", "--target", TARGET, "--json"], env=env)
    require(plan.get("ok") is True, f"release-seal plan failed: {plan}")
    pdata = plan["data"]
    require(pdata["summary"]["node_count"] == 7, f"release plan node count drifted: {pdata['summary']}")
    require(pdata.get("schedule_error", "") == "", f"release plan scheduler error: {pdata.get('schedule_error')}")
    nodes = {node["job_local_name"]: node for node in pdata["schedule"]["nodes"]}
    require(set(nodes) == EXPECTED_JOBS, f"release plan job set drifted: {sorted(nodes)}")
    require(all(node["writes_known"] is True for node in nodes.values()), "one or more workflow nodes have unknown writes")
    source = nodes["source-hygiene"]
    require(source["read_only"] is True and source["writes"] == [], f"source-hygiene is not read-only: {source}")
    device = nodes["device-compat"]
    require(device["resource_class"] == "device-exclusive", f"device resource class drifted: {device}")
    require("mmrl-adb-device" in device["global_reservations"], f"device reservation missing: {device}")
    for name in EXPECTED_JOBS - {"source-hygiene", "device-compat"}:
        require(nodes[name]["resource_class"] == "gradle-heavy", f"{name} resource class drifted: {nodes[name]}")


def validate_preflight(exe: str, env: dict[str, str]) -> None:
    preflight = run_json(
        devtool_base(exe) + ["preflight", "--target", TARGET, "--workflow", "release-seal", "--json"],
        env=env,
        expect_success=False,
    )
    data = preflight.get("data", {})
    bad_statuses = {"missing", "error", "incompatible", "unsafe", "unsupported"}
    bad_checks = [check for check in data.get("checks", []) if check.get("status") in bad_statuses]
    bad_tools = [tool for tool in data.get("tools", []) if tool.get("status") in bad_statuses]

    if preflight.get("ok") is True and data.get("healthy") is True and preflight["__returncode"] == 0:
        require(not bad_checks, f"preflight reports bad checks despite healthy=true: {bad_checks}")
        require(not bad_tools, f"preflight reports bad tools despite healthy=true: {bad_tools}")
        return

    allow_structural = os.environ.get("MMRL_G1_ALLOW_NON_TERMUX_STRUCTURAL") == "1"
    only_host_mismatch = bool(bad_checks) and all(
        check.get("section") == "environment"
        and check.get("name") == "native-termux:termux"
        and check.get("status") == "incompatible"
        for check in bad_checks
    )
    # Structural mode is only for the off-device artifact self-test. It may
    # tolerate the expected desktop-vs-Termux host mismatch, but never a tool
    # requirement failure.
    if allow_structural and only_host_mismatch and not bad_tools:
        print("mmrl-dt-g1: non-Termux structural validation mode accepted native-termux host mismatch", file=sys.stderr)
        return

    raise GateError(
        "release-seal preflight is not healthy: "
        f"return={preflight['__returncode']} "
        f"bad_checks={bad_checks} bad_tools={bad_tools} "
        f"stderr={preflight['__stderr']}"
    )


def validate_raw_gradle_boundaries() -> None:
    occurrences: dict[Path, list[int]] = {}
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if any(part in SKIP_DIRS for part in rel.parts):
            continue
        if path.name in {"gradlew", "gradlew.bat"}:
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES and path.name not in {"mise.toml"}:
            continue
        try:
            body = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        lines = [idx for idx, line in enumerate(body.splitlines(), start=1) if RAW_GRADLE_RE.search(line)]
        if lines:
            occurrences[rel] = lines

    unexpected = set(occurrences) - ALLOWED_RAW_GRADLE_FILES
    require(not unexpected, f"unexpected raw-Gradle ingress found: {sorted(map(str, unexpected))}")

    # Canonical local/release surfaces must already be clean in G1.
    for rel in (Path("build-android-app.sh"), Path("build-release-apk.sh"), Path("scripts/run-mmrl-release-seal.sh")):
        body = (ROOT / rel).read_text(encoding="utf-8")
        require("devtoolw" in body, f"canonical script does not enter via devtoolw: {rel}")
        require(not RAW_GRADLE_RE.search(body), f"canonical script still invokes raw Gradle: {rel}")

    device = ROOT / "scripts/run-mmrl-device-validation.sh"
    require(device in [ROOT / p for p in ALLOWED_RAW_GRADLE_FILES], "internal gate error: device compatibility allowlist missing")
    device_body = device.read_text(encoding="utf-8")
    require("MMRL-DT-X01 compatibility boundary" in device_body, "device raw-Gradle exception is not explicitly labeled")

    if occurrences:
        print("mmrl-dt-g1 classified raw-Gradle boundaries:")
        for rel in sorted(occurrences, key=str):
            owner = "X02" if rel == Path("scripts/run-mmrl-device-validation.sh") else "X03" if rel in {Path(".github/workflows/mmrl-release-seal.yml"), Path("mise.toml")} else "documentation"
            print(f"  - {rel}: lines {occurrences[rel]} -> {owner}")


def main() -> int:
    exe = os.environ.get("DEVTOOL_GATE_EXECUTABLE") or shutil.which("devtool")
    require(bool(exe), "Devtool executable is unavailable")
    assert exe is not None

    with tempfile.TemporaryDirectory(prefix="mmrl-dt-g1-") as tmp:
        env = os.environ.copy()
        env["DEVTOOL_GLOBAL_CONFIG"] = str(Path(tmp) / "nonexistent-global.toml")
        env.setdefault("PYTHONDONTWRITEBYTECODE", "1")

        validate_repository_scripts(env)
        validate_config(exe, env)
        validate_wrapper(exe, env)
        validate_jobs_and_workflows(exe, env)
        validate_raw_gradle_boundaries()
        validate_preflight(exe, env)

    print("MMRL-DT-G1 foundation gate: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GateError as exc:
        print(f"MMRL-DT-G1 foundation gate: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
