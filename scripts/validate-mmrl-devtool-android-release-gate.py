#!/usr/bin/env python3
"""MMRL-DT-G2 authoritative Android validation/release gate.

This gate audits the complete X02 + G2-R01 window, executes one durable Devtool validation
session, executes one canonical AOPS04 release with required instrumentation and
install smoke, then verifies the immutable release evidence. During isolated
artifact application it temporarily projects project-owned signing inputs from
the primary repository into the transaction worktree; secrets are never copied
into the overlay and are removed on every exit path.
"""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Any, Iterator

ROOT = Path(__file__).resolve().parents[1]
TARGET = "mmrl_android"
HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")


class GateError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise GateError(message)


def command_env(tmp: Path) -> dict[str, str]:
    env = os.environ.copy()
    # Nested Devtool qualification is a separate canonical run inside the
    # already-isolated artifact worktree. Never inherit the outer apply run or
    # transaction identity: earlier failed gate attempts reused the
    # apply run id and allowed execution-lifecycle failures to obscure the real
    # child boundary. The worktree path itself remains the explicit --repo.
    for key in (
        "DEVTOOL_RUN_ID",
        "DEVTOOL_TRANSACTION_ROOT",
        "DEVTOOL_TRANSACTION_REPO_ROOT",
        "DEVTOOL_TRANSACTION_PRIMARY_REPO_ROOT",
        "DEVTOOL_TRANSACTION_COMMIT_MODE",
        "DEVTOOL_TRANSACTION_ISOLATION",
    ):
        env.pop(key, None)
    # Host toolchain locations/bootstrap receipts are legitimately global Devtool
    # state. Preserve DEVTOOL_GLOBAL_CONFIG so the native-Termux gate can reuse
    # canonical SDK/bootstrap receipts. Repository policy is independently proved
    # by the targeted X02/R01 structural validators before runtime qualification.
    env["DEVTOOL_TRACEBACK"] = "1"
    env.setdefault("PYTHONDONTWRITEBYTECODE", "1")
    return env


def run_capture(argv: list[str], *, env: dict[str, str]) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(
        argv,
        cwd=ROOT,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode != 0:
        raise GateError(
            f"command failed ({proc.returncode}): {' '.join(argv)}\n"
            f"stdout:\n{proc.stdout}\nstderr:\n{proc.stderr}"
        )
    return proc


def run_json(argv: list[str], *, env: dict[str, str]) -> dict[str, Any]:
    proc = run_capture(argv, env=env)
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise GateError(
            f"command did not emit JSON: {' '.join(argv)}\n{proc.stdout}\n{proc.stderr}"
        ) from exc


def run_live(argv: list[str], *, env: dict[str, str], label: str) -> None:
    print(f"\n=== MMRL-DT-G2: {label} ===", flush=True)
    proc = subprocess.run(argv, cwd=ROOT, env=env, check=False)
    if proc.returncode != 0:
        raise GateError(f"{label} failed with exit code {proc.returncode}: {' '.join(argv)}")
    print(f"=== MMRL-DT-G2: {label}: PASS ===", flush=True)


def devtool_base(exe: str) -> list[str]:
    return [exe, "--repo", str(ROOT), "--target", TARGET]


def java_property(path: Path, name: str) -> str:
    # MMRL's signing.properties uses ordinary key=value Java properties. We only
    # inspect keyStore so a relative ignored keystore can be projected alongside
    # the properties file; password values are never parsed or printed.
    pattern = re.compile(rf"^\s*{re.escape(name)}\s*[:=]\s*(.*?)\s*$")
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not raw or raw.lstrip().startswith(("#", "!")):
            continue
        match = pattern.match(raw)
        if match:
            return match.group(1)
    return ""


@contextmanager
def signing_projection() -> Iterator[str]:
    """Provide project-owned signing inputs inside the isolated gate worktree.

    Prefer the developer's existing ignored signing.properties/keystore. When no
    private signer is configured, generate a disposable validation keystore
    outside the repository and project only a temporary ignored
    signing.properties into the transaction worktree. This mirrors MMRL CI: G2
    still exercises the real project signing path and Devtool signature/cert
    verification without requiring production signing secrets to exist locally.
    """
    local_props = ROOT / "signing.properties"
    primary_raw = str(os.environ.get("DEVTOOL_TRANSACTION_PRIMARY_REPO_ROOT") or "").strip()
    primary = Path(primary_raw).expanduser().resolve() if primary_raw else ROOT
    primary_props = primary / "signing.properties"
    created: list[Path] = []
    temp_signing: tempfile.TemporaryDirectory[str] | None = None
    source = "worktree" if local_props.is_file() else ""

    if not local_props.is_file() and primary_props.is_file():
        shutil.copy2(primary_props, local_props)
        os.chmod(local_props, 0o600)
        created.append(local_props)
        source = "primary-project"

    if not local_props.is_file():
        keytool = shutil.which("keytool")
        require(bool(keytool), "release signing is not configured and keytool is unavailable for the G2 validation signer")
        assert keytool is not None
        temp_signing = tempfile.TemporaryDirectory(prefix="mmrl-g2-signing-")
        signing_dir = Path(temp_signing.name)
        keystore = signing_dir / "mmrl-g2-validation.jks"
        password = "mmrl-g2-validation"
        alias = "mmrl-g2-validation"
        proc = subprocess.run(
            [
                keytool, "-genkeypair", "-noprompt",
                "-keystore", str(keystore),
                "-storetype", "JKS",
                "-storepass", password,
                "-keypass", password,
                "-alias", alias,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=MMRL G2 Validation,O=MMRL,C=US",
            ],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        require(
            proc.returncode == 0 and keystore.is_file(),
            "could not create the ephemeral G2 project-signing keystore"
            + (f": {proc.stderr.strip()}" if proc.stderr.strip() else ""),
        )
        local_props.write_text(
            "\n".join([
                f"keyStore={keystore}",
                f"keyStorePassword={password}",
                f"keyAlias={alias}",
                f"keyPassword={password}",
                "",
            ]),
            encoding="utf-8",
        )
        os.chmod(local_props, 0o600)
        created.append(local_props)
        source = "ephemeral-validation"

    keystore_raw = java_property(local_props, "keyStore")
    require(bool(keystore_raw), "signing.properties does not declare keyStore")
    configured = Path(os.path.expandvars(os.path.expanduser(keystore_raw)))
    local_store = configured if configured.is_absolute() else ROOT / configured
    if not local_store.is_file() and not configured.is_absolute() and primary != ROOT:
        primary_store = primary / configured
        require(primary_store.is_file(), f"release signing keyStore is unavailable: {primary_store}")
        local_store.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(primary_store, local_store)
        os.chmod(local_store, 0o600)
        created.append(local_store)
    require(local_store.is_file(), f"release signing keyStore is unavailable: {local_store}")

    print(f"MMRL-DT-G2 signing source: {source}", flush=True)
    try:
        yield source
    finally:
        for path in reversed(created):
            try:
                path.unlink(missing_ok=True)
            except OSError as exc:
                print(f"MMRL-DT-G2 signing projection cleanup warning: {path}: {exc}", file=sys.stderr)
        if temp_signing is not None:
            temp_signing.cleanup()


def validate_structural(exe: str, env: dict[str, str]) -> None:
    # Re-run the complete targeted X02 + R01 contracts against the exact Devtool
    # runtime selected by G2 rather than depending on PATH discovery in children.
    env["DEVTOOL_X02_EXECUTABLE"] = exe
    env["DEVTOOL_G2_R01_EXECUTABLE"] = exe
    run_capture([sys.executable, "scripts/validate-mmrl-devtool-g2-r01-remediation.py"], env=env)
    run_capture([sys.executable, "scripts/validate-mmrl-source-hygiene.py"], env=env)
    run_capture(
        [sys.executable, "-m", "py_compile",
         "scripts/validate-mmrl-devtool-android-release-authority.py",
         "scripts/validate-mmrl-devtool-g2-r01-remediation.py",
         "scripts/validate-mmrl-devtool-android-release-gate.py",
         "scripts/validate-mmrl-source-hygiene.py"],
        env=env,
    )
    run_capture(["bash", "-n", "scripts/run-mmrl-release-seal.sh", "scripts/run-mmrl-device-validation.sh", "build-release-apk.sh"], env=env)

    migrate = run_json(devtool_base(exe) + ["config", "migrate", "--check", "--json"], env=env)
    require(migrate.get("ok") is True and migrate.get("data", {}).get("needs_migration") is False,
            f"canonical config check failed: {migrate}")
    wrapper = run_json(devtool_base(exe) + ["wrapper", "seal", "--json"], env=env)
    require(wrapper.get("ok") is True and wrapper.get("data", {}).get("ok") is True,
            f"wrapper seal failed: {wrapper}")

    # `validate` must resolve to Devtool's native Android resumable pipeline.
    # The repository EXO DAG remains available as `repository-check`, but it may
    # not shadow this canonical action.
    validate_plan = run_json(devtool_base(exe) + ["plan", "validate", "--json"], env=env)
    require(validate_plan.get("ok") is True, f"native validation plan failed: {validate_plan}")
    validate_data = validate_plan.get("data", {})
    validate_summary = validate_data.get("summary", {})
    require(validate_summary.get("node_count") == 1 and validate_summary.get("target_local_job_count") == 0,
            f"validate is still resolving through a target-local workflow: {validate_summary}")
    validate_commands = [str(item.get("name") or "") for item in validate_data.get("commands", [])]
    expected_validation = [
        "android-validation-preflight",
        "android-validation-compile",
        "android-validation-resources",
        "android-validation-unit-test",
        "android-validation-package",
        "android-validation-lint",
        "android-validation-finalize",
    ]
    require(validate_commands == expected_validation,
            f"native Android resumable phases drifted: {validate_commands}")

    repository_plan = run_json(devtool_base(exe) + ["plan", "repository-check", "--json"], env=env)
    require(repository_plan.get("ok") is True and repository_plan.get("data", {}).get("summary", {}).get("node_count") == 6,
            f"repository-check EXO DAG drifted: {repository_plan}")

    device_plan = run_json(devtool_base(exe) + ["plan", "device", "--json"], env=env)
    require(device_plan.get("ok") is True, f"native device plan failed: {device_plan}")
    nodes = device_plan.get("data", {}).get("schedule", {}).get("nodes", [])
    require(len(nodes) == 1 and nodes[0].get("runner") == "android" and nodes[0].get("action") == "instrumentation",
            f"native device workflow drifted: {nodes}")
    require(nodes[0].get("resource_class") == "device-exclusive" and "mmrl-adb-device" in nodes[0].get("global_reservations", []),
            f"native device reservation drifted: {nodes[0]}")

    release_check = run_json(devtool_base(exe) + ["plan", "release-check", "--json"], env=env)
    require(release_check.get("ok") is True and release_check.get("data", {}).get("summary", {}).get("node_count") == 7,
            f"release-check DAG drifted: {release_check}")

    # Side-effect-free AOPS04 proof using the exact installed Devtool runtime.
    from devtool.android.release import AndroidReleaseOrchestrator, release_options
    from devtool.config import load_project_config

    cfg = load_project_config(str(ROOT), None, target_name=TARGET)
    options = release_options(cfg)
    require(options.variant == "officialRelease", f"release variant drifted: {options}")
    require(options.device_tests == "required", f"G2 requires fail-closed release instrumentation: {options}")
    require(options.install_smoke and options.upgrade_smoke, f"release smoke policy drifted: {options}")
    require(options.verify_signature and options.verify_certificate_identity, f"release signing policy drifted: {options}")
    _cfg, _options, phases = AndroidReleaseOrchestrator(cfg).plan()
    require([name for name, _ in phases] == ["unit-tests", "lint", "package", "instrumentation"],
            f"AOPS04 phase graph drifted: {[name for name, _ in phases]}")


def validate_release_evidence(path: Path) -> None:
    require(path.is_file(), f"AOPS04 did not create explicit release evidence: {path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    require(payload.get("schema_version") == 1, f"unexpected release evidence schema: {payload.get('schema_version')}")
    require(payload.get("report_kind") == "devtool-android-release-evidence", "release evidence kind drifted")
    require(payload.get("target") == TARGET and payload.get("variant") == "officialRelease", "release evidence target/variant drifted")
    require(payload.get("verdict") == "passed", f"release evidence verdict is not passed: {payload.get('verdict')}")

    expected_hash = str(payload.get("evidence_sha256") or "")
    unhashed = dict(payload)
    unhashed.pop("evidence_sha256", None)
    actual_hash = hashlib.sha256(
        json.dumps(unhashed, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    require(HEX64.fullmatch(expected_hash) is not None and expected_hash.lower() == actual_hash,
            "release evidence SHA-256 seal is invalid")

    source = payload.get("source") or {}
    require(bool(source.get("git_head")) and bool(source.get("devtool_version")), f"release source identity incomplete: {source}")

    execution = payload.get("execution") or {}
    require(execution.get("authority") == "android-environment-v1", f"release execution authority drifted: {execution.get('authority')}")
    require(isinstance(execution.get("android_environment"), dict), "release evidence omitted resolved Android environment")
    require(isinstance(execution.get("artifact_pair"), dict), "release evidence omitted canonical artifact pair")
    snapshot = execution.get("android_execution_snapshot") or {}
    require(bool(snapshot.get("snapshot_id")) and bool(snapshot.get("run_attachment_id")), f"release execution snapshot missing: {snapshot}")

    signing = payload.get("signing") or {}
    require(signing.get("mode") == "project" and signing.get("policy") == "release-required", f"release signing authority drifted: {signing}")
    certificate = str(signing.get("certificate_sha256") or "")
    require(HEX64.fullmatch(certificate) is not None, f"release evidence lacks verified certificate SHA-256: {certificate!r}")
    verified = signing.get("verified_artifacts") or []
    require(bool(verified) and all(str(item.get("status") or "") == "passed" for item in verified if isinstance(item, dict)),
            f"release artifact signature verification incomplete: {verified}")

    device = payload.get("device") or {}
    lease = device.get("lease") or {}
    require(device.get("authority") == "android-device-lease-v1", f"release device authority drifted: {device.get('authority')}")
    require(bool(lease.get("lease_id")) and bool(lease.get("serial")) and str(lease.get("boot_completed") or "") == "1",
            f"release evidence lacks a qualified device lease: {lease}")

    gates = {str(item.get("name")): item for item in payload.get("gates", []) if isinstance(item, dict)}
    for name in ("unit-tests", "lint", "package", "instrumentation", "signature-verification", "install-smoke"):
        gate = gates.get(name)
        require(bool(gate), f"release evidence omitted gate {name}")
        require(gate.get("required") is True and gate.get("status") == "passed", f"release gate {name} did not pass: {gate}")
    upgrade = gates.get("upgrade-smoke")
    require(bool(upgrade), "release evidence omitted upgrade-smoke policy result")
    require(upgrade.get("status") in {"passed", "skipped"}, f"upgrade-smoke has invalid state: {upgrade}")
    if upgrade.get("status") == "skipped":
        require(upgrade.get("required") is False, f"skipped upgrade-smoke must be non-required: {upgrade}")

    artifacts = payload.get("artifacts") or []
    require(bool(artifacts), "release evidence contains no release artifacts")
    require(any(str(item.get("path") or item.get("file") or "").lower().endswith(".apk") for item in artifacts if isinstance(item, dict)),
            f"release evidence does not contain an APK artifact: {artifacts}")


def require_native_termux() -> None:
    prefix = str(os.environ.get("PREFIX") or "")
    require(prefix.startswith("/data/data/com.termux/files/usr"),
            f"authoritative G2 runtime requires native Termux; PREFIX={prefix or '<unset>'}")


def main() -> int:
    exe = os.environ.get("DEVTOOL_G2_EXECUTABLE") or shutil.which("devtool")
    require(bool(exe), "Devtool executable is unavailable")
    assert exe is not None
    structural_only = str(os.environ.get("MMRL_G2_STRUCTURAL_ONLY") or "").strip().lower() in {"1", "true", "yes", "on"}

    with tempfile.TemporaryDirectory(prefix="mmrl-dt-g2-") as tmp_raw:
        tmp = Path(tmp_raw)
        env = command_env(tmp)
        validate_structural(exe, env)
        if structural_only:
            print("MMRL-DT-G2 Android release gate: STRUCTURAL PASS (runtime gate intentionally skipped)")
            return 0

        require_native_termux()
        with signing_projection() as signing_source:
            # One authoritative durable validation session. --require-complete
            # prevents a paused/partial checkpoint sequence from qualifying G2.
            run_live(
                [exe, "--repo", str(ROOT), "--target", TARGET,
                 "--execution-environment", "native-termux",
                 "--bootstrap-downloads", "missing",
                 "validate", "--sequential", "--restart-session", "--complete", "--require-complete",
                 "--failure-action", "pause"],
                env=env,
                label="durable Android validation session",
            )

            evidence = tmp / "mmrl-g2-release-evidence.json"
            run_live(
                [exe, "--repo", str(ROOT), "--target", TARGET,
                 "--execution-environment", "native-termux",
                 "--bootstrap-downloads", "missing",
                 "release", "--device-tests", "required",
                 "--evidence-output", str(evidence)],
                env=env,
                label="AOPS04 authoritative release",
            )
            validate_release_evidence(evidence)
            print(f"MMRL-DT-G2 qualified signing source: {signing_source}", flush=True)

    print("MMRL-DT-G2 Android release gate: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except GateError as exc:
        print(f"MMRL-DT-G2 Android release gate: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
