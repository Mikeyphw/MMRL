#!/usr/bin/env python3
"""Static O11 source hygiene gate for MMRL release snapshots."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FAILURES: list[str] = []


def fail(message: str) -> None:
    FAILURES.append(message)


def text(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"missing required file: {path}")
        return ""
    return p.read_text(encoding="utf-8", errors="replace")


def require_contains(path: str, *needles: str) -> None:
    body = text(path)
    for needle in needles:
        if needle not in body:
            fail(f"{path} is missing required text: {needle}")


def check_wrapper() -> None:
    props = text("gradle/wrapper/gradle-wrapper.properties")
    if "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.0-bin.zip" not in props:
        fail("wrapper must pin the Gradle 9.5.0 binary distribution URL")
    match = re.search(r"^distributionSha256Sum=([0-9a-f]{64})$", props, re.M)
    if not match:
        fail("wrapper must set a 64-hex distributionSha256Sum")
    elif match.group(1) != "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746":
        fail("wrapper checksum is not the published Gradle 9.5.0-bin.zip checksum")


def check_gradle_release_gates() -> None:
    app = text("app/build.gradle.kts")
    for needle in (
        "testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"",
        "abortOnError = true",
        "warningsAsErrors = true",
        "checkDependencies = true",
        "assembleOfficialDebug",
        "assembleOfficialRelease",
        "assembleOfficialPlaystore",
        "connectedOfficialDebugAndroidTest",
        "resolveVersionCode(31320)",
        "releaseSigningProperties = project.releaseSigningProperties()",
        "refusing to create unsigned or debug-signed release/playstore artifacts",
        "matchingFallbacks += listOf(\"release\")",
        "variant.sources.assets?.addGeneratedSourceDirectory",
    ):
        if needle not in app:
            fail(f"app Gradle release seal is missing {needle}")
    if "signingConfigs.getByName(\"debug\")" in app:
        fail("release/playstore signing must not silently fall back to debug signing")
    if "startsWith(\"merge\") && name.endsWith(\"Assets\")" in app:
        fail("generated Ash assets must be wired through variant sources, not guessed task names")

    project_ext = text("build-logic/src/main/kotlin/ProjectExt.kt")
    for needle in (
        "execOrNull",
        "mmrl.versionCode",
        "mmrl.commitCount",
        "mmrl.commitId",
        "ReleaseSigningProperties",
        "Incomplete signing.properties",
    ):
        if needle not in project_ext:
            fail(f"ProjectExt release hygiene is missing {needle}")

    datastore = text("datastore/build.gradle.kts")
    prefs = text("datastore/src/main/kotlin/com/dergoogler/mmrl/datastore/model/UserPreferences.kt")
    if "WEBUIX_PACKAGE_NAME" not in datastore or "BuildConfig.WEBUIX_PACKAGE_NAME" not in prefs:
        fail("DataStore WebUIX package default must be variant-owned through BuildConfig")

    root_build = text("build.gradle.kts")
    if "delete(subprojects.map { it.layout.buildDirectory })" not in root_build:
        fail("root clean must remove subproject build outputs")

    devtool = text(".devtool.toml")
    for needle in (":app:assembleOfficialPlaystore", "-Pmmrl.fullLint=true", ":platform:testDebugUnitTest", ":platform:testNativeContracts"):
        if needle not in devtool:
            fail(f"Devtool final validation metadata is missing {needle}")
    platform = text("platform/build.gradle.kts")
    if 'ndkVersion = NDK_VERSION' not in platform:
        fail("platform NDK version must use the shared NDK_VERSION constant")
    if "testNativeContracts" not in platform:
        fail("platform must expose the host native contract test task")

def check_source_hygiene() -> None:
    gitignore = text(".gitignore")
    for needle in (".devtool/build-artifacts/", ".devtool/artifacts.d/", "*.apk", "*.aab", "build-logs/"):
        if needle not in gitignore:
            fail(f".gitignore is missing generated-output exclusion {needle}")
    pack = text("pack_repo.sh")
    if ".tar.zst" not in pack or "--zstd" not in pack:
        fail("pack_repo.sh must use matching .tar.zst naming with zstd compression")
    release = text("build-release-apk.sh")
    if "build_variant \"$FLAVOR\" playstore" not in release:
        fail("build-release-apk.sh all mode must build the playstore variant")

    if (ROOT / ".git").is_dir():
        import subprocess
        tracked = subprocess.run(
            ["git", "ls-files", ".devtool", "*.apk", "*.aab", "*.aar", "build-logs"],
            cwd=ROOT,
            check=False,
            text=True,
            capture_output=True,
        ).stdout.splitlines()
        forbidden = [path for path in tracked if path.startswith(".devtool/build-artifacts/") or path.startswith(".devtool/artifacts.d/") or path.endswith((".apk", ".aab", ".aar")) or path.startswith("build-logs/")]
        if forbidden:
            fail("generated artifacts are tracked in source: " + ", ".join(forbidden[:12]))


def check_room_schemas() -> None:
    db = text("app/src/main/kotlin/com/dergoogler/mmrl/database/AppDatabase.kt")
    match = re.search(r"version\s*=\s*(\d+)", db)
    current_version = int(match.group(1)) if match else 0
    if current_version < 1:
        fail("could not determine AppDatabase current schema version")

    base = ROOT / "app" / "schemas" / "com.dergoogler.mmrl.database.AppDatabase"
    if not base.is_dir():
        fail("missing Room schema directory for app database")
        return
    versions = sorted(int(p.stem) for p in base.glob("*.json") if p.stem.isdigit())
    if not versions:
        fail("no app Room schema JSON files found")
        return
    if versions[-1] < 19:
        fail(f"Room schema exports are too stale: newest existing schema is {versions[-1]}")
    require_contains("build-logic/src/main/kotlin/RoomConventionPlugin.kt", "room.schemaLocation", "room.incremental")
    if "exportSchema = true" not in db:
        fail("AppDatabase must keep Room schema export enabled")
    for schema in base.glob("*.json"):
        data = json.loads(schema.read_text(encoding="utf-8"))
        actual = data.get("database", {}).get("version")
        if actual != int(schema.stem):
            fail(f"Room schema {schema.relative_to(ROOT)} declares version {actual}, expected {schema.stem}")

def check_final_seal_files() -> None:
    for path in (
        ".github/workflows/mmrl-release-seal.yml",
        "scripts/run-mmrl-release-seal.sh",
        "scripts/validate-mmrl-source-hygiene.py",
        "docs/MMRL_FINAL_RELEASE_SEAL.md",
        "app/src/androidTest/kotlin/com/dergoogler/mmrl/release/FinalManifestIntegrationInstrumentedTest.kt",
        "app/src/androidTest/kotlin/com/dergoogler/mmrl/release/FinalWorkManagerAndLifecycleInstrumentedTest.kt",
        "app/src/androidTest/kotlin/com/dergoogler/mmrl/release/FinalFileProviderContentUriInstrumentedTest.kt",
        "app/src/androidTest/kotlin/com/dergoogler/mmrl/release/FinalRoomMigrationInstrumentedTest.kt",
    ):
        if not (ROOT / path).is_file():
            fail(f"missing final seal artifact: {path}")


check_wrapper()
check_gradle_release_gates()
check_source_hygiene()
check_room_schemas()
check_final_seal_files()

if FAILURES:
    for failure in FAILURES:
        print(f"release-hygiene: FAIL: {failure}", file=sys.stderr)
    raise SystemExit(1)

print("release-hygiene: PASS")
