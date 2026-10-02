#!/usr/bin/env python3
"""Targeted validator for MMRL-DT-G2-R01.

This validator is deliberately structural/planning-only. It proves the
native-Termux/bootstrap, validation-action ownership, resumable-phase, wrapper,
and required-device policy remediation without running Gradle, instrumentation,
signing, or AOPS04 release. MMRL-DT-G2 owns those authoritative runtime gates.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tomllib
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TARGET = "mmrl_android"

class ValidationError(RuntimeError):
    pass

def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)

def run(argv: list[str], *, env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(argv, cwd=ROOT, env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if proc.returncode != 0:
        raise ValidationError(f"command failed ({proc.returncode}): {' '.join(argv)}\nstdout:\n{proc.stdout}\nstderr:\n{proc.stderr}")
    return proc

def run_json(argv: list[str], *, env: dict[str, str]) -> dict[str, Any]:
    proc = run(argv, env=env)
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise ValidationError(f"command did not emit JSON: {' '.join(argv)}\n{proc.stdout}\n{proc.stderr}") from exc

def base(exe: str) -> list[str]:
    return [exe, "--repo", str(ROOT), "--target", TARGET]

def main() -> int:
    exe = os.environ.get("DEVTOOL_G2_R01_EXECUTABLE") or os.environ.get("DEVTOOL_X02_EXECUTABLE") or shutil.which("devtool")
    require(bool(exe), "Devtool executable is unavailable")
    assert exe is not None
    env = os.environ.copy()
    env.setdefault("PYTHONDONTWRITEBYTECODE", "1")

    # Re-run the complete targeted X02 authority contract after remediation.
    env["DEVTOOL_X02_EXECUTABLE"] = exe
    run([sys.executable, "scripts/validate-mmrl-devtool-android-release-authority.py"], env=env)

    cfg = tomllib.loads((ROOT / ".devtool.toml").read_text(encoding="utf-8"))
    target = cfg["targets"][TARGET]
    android = target["android"]
    bootstrap = cfg.get("bootstrap", {})
    validation = cfg["validation"]
    phases = validation.get("phases", {})

    require(target.get("execution_environment") == "native-termux", f"target execution environment drifted: {target.get('execution_environment')}")
    require(android.get("prefer_native_termux") is True, f"native-Termux preference is not fail-closed: {android}")
    require(bootstrap.get("downloads") == "missing", f"bootstrap downloads policy drifted: {bootstrap}")
    require(bootstrap.get("network") == "allow", f"bootstrap network policy drifted: {bootstrap}")
    require(bootstrap.get("fail_on_missing_tools") is True, f"bootstrap missing-tool policy drifted: {bootstrap}")
    require(android.get("release", {}).get("device_tests") == "required", f"full release instrumentation is not required: {android.get('release', {})}")
    require(validation.get("resumable") is True, f"native validation is not resumable: {validation}")
    require(phases.get("dependencies") is False and phases.get("instrumentation") is False and phases.get("finalize") is True,
            f"native validation phase ownership drifted: {phases}")

    workflows = target.get("workflows", {})
    require("validate" not in workflows, "repository workflow still shadows native Devtool validate")
    require("repository-check" in workflows and len(workflows["repository-check"]) == 6,
            f"repository-check workflow drifted: {workflows.get('repository-check')}")
    wrapper = cfg.get("wrapper", {}).get("commands", {}).get("repository-check", {})
    require(wrapper.get("workflow") == "repository-check" and wrapper.get("target") == TARGET,
            f"repository-check wrapper drifted: {wrapper}")

    plan = run_json(base(exe) + ["plan", "validate", "--json"], env=env)
    require(plan.get("ok") is True, f"native validation plan failed: {plan}")
    commands = [str(item.get("name") or "") for item in plan.get("data", {}).get("commands", [])]
    expected = [
        "android-validation-preflight",
        "android-validation-compile",
        "android-validation-resources",
        "android-validation-unit-test",
        "android-validation-package",
        "android-validation-lint",
        "android-validation-finalize",
    ]
    require(commands == expected, f"native validation phase plan drifted: {commands}")

    repo_plan = run_json(base(exe) + ["plan", "repository-check", "--json"], env=env)
    require(repo_plan.get("ok") is True and repo_plan.get("data", {}).get("summary", {}).get("node_count") == 6,
            f"repository-check plan drifted: {repo_plan}")

    print("MMRL-DT-G2-R01 targeted remediation validation: PASS")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as exc:
        print(f"MMRL-DT-G2-R01 targeted remediation validation: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
