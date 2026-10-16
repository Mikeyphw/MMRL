#!/usr/bin/env python3
"""Offline GitHub workflow contract and remote-only OfficialDebug evidence producer.

This never signs or qualifies production releases. Device tests are COMPILED,
not executed: Devtool AOPS02/AOPS04 remains the MMRL device/release authority.
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
import tomllib

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.mikeyphw.mmrl.debug'
STAGED_APK = 'MMRL-OfficialDebug-verified.apk'
MANUAL = '.github/workflows/devtool-android-build.yml'
PREEXISTING = '.github/workflows/mmrl-release-seal.yml'


def require(ok: bool, msg: str) -> None:
    if not ok:
        raise ValueError(msg)


def read(relative: str) -> str:
    p = ROOT / relative
    require(p.is_file() and not p.is_symlink(), f'missing/unsafe file: {relative}')
    return p.read_text(encoding='utf-8')


def static() -> None:
    cfg = tomllib.loads(read('.devtool.toml'))
    target = cfg.get('targets', {}).get('mmrl_android', {})
    require(target.get('runner') == 'android', 'missing MMRL Devtool target')
    require(target.get('tasks', {}).get('build') == [':app:assembleOfficialDebug'],
            'canonical Devtool debug task changed')
    release = target.get('android', {}).get('release', {})
    require(release.get('variant') == 'officialRelease' and release.get('device_tests') == 'required'
            and release.get('verify_signature') is True, 'MMRL release authority changed')
    require(target.get('android', {}).get('instrumentation', {}).get('policy') == 'required',
            'device instrumentation gate changed')
    require(target.get('android', {}).get('signing', {}).get('release', {}).get('mode') == 'project',
            'production signing authority changed')
    require(target.get('gradle', {}).get('version') == '9.7.1', 'Gradle version changed')
    app = read('app/build.gradle.kts')
    for token in ('mmrlForkApplicationId = "com.mikeyphw.mmrl"', 'applicationIdSuffix = ".debug"',
                  'create("official")', 'assembleOfficialDebug',
                  'refusing to create an unsigned or debug-signed release artifact'):
        require(token in app, f'MMRL application contract changed: {token}')
    ext = read('build-logic/src/main/kotlin/ProjectExt.kt')
    for token in ('const val COMPILE_SDK = 36', 'const val NDK_VERSION = "29.0.14206865"'):
        require(token in ext, f'MMRL toolchain changed: {token}')
    props = read('gradle/wrapper/gradle-wrapper.properties')
    require('gradle-9.7.1-bin.zip' in props and 'distributionSha256Sum=' in props,
            'Gradle wrapper must be pinned')
    wrapper = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
    require(wrapper.is_file() and not wrapper.is_symlink() and wrapper.stat().st_size > 0,
            'Gradle wrapper jar is missing')
    existing = read(PREEXISTING)
    for token in ('pull_request:', 'push:', 'workflow_dispatch:',
                  'Prepare ephemeral CI validation signing', "matrix.task-set == 'release'",
                  ':app:assembleOfficialDebug', ':app:assembleOfficialRelease'):
        require(token in existing, f'existing release-seal CI changed: {token}')
    manual = read(MANUAL)
    require('workflow_dispatch:' in manual and '\n  pull_request:' not in manual and '\n  push:' not in manual,
            'manual build must be dispatch-only')
    for token in ('options: [officialDebug]', 'options: [app]', 'options: [apk]',
                  "options: ['.']", 'test "$(git rev-parse HEAD)" = "$GITHUB_SHA"',
                  'scripts/validate-mmrl-source-hygiene.py',
                  ':app:testOfficialDebugUnitTest', ':platform:testNativeContracts',
                  ':app:compileOfficialDebugAndroidTestKotlin', ':app:lintOfficialDebug',
                  ':app:assembleOfficialDebug', "'ndk;29.0.14206865'", "'cmake;3.22.1'",
                  "java-version: '21'", 'apksigner', 'aapt',
                  'actions/upload-artifact@v4', 'if-no-files-found: error',
                  '--evidence', 'retention-days: 7',
                  'persist-credentials: false'):
        require(token in manual or (token in ('apksigner', 'aapt') and token in read('scripts/verify-mmrl-github-actions.py')),
                f'manual workflow safeguard missing: {token}')
    for forbidden in ('secrets.', 'pull_request_target:', 'permissions: write-all',
                      'assembleOfficialRelease', 'bundleOfficialRelease',
                      'signing.properties', 'softprops/action-gh-release'):
        require(forbidden not in manual, f'forbidden remote debug workflow capability: {forbidden}')
    require(re.search(r'(?m)^permissions:\s*\n\s+contents: read\s*$', manual) is not None,
            'workflow must use read-only permissions')
    try:
        import yaml
    except ImportError:
        pass
    else:
        parsed = yaml.safe_load(manual)
        require(isinstance(parsed, dict) and isinstance(parsed.get('jobs'), dict), 'invalid workflow YAML')
        triggers = parsed.get('on', parsed.get(True))
        require(isinstance(triggers, dict) and isinstance(triggers.get('workflow_dispatch'), dict),
                'dispatch block missing')
        inputs = triggers['workflow_dispatch']['inputs']
        for name, expected in [('project_dir', ['.']), ('module', ['app']),
                               ('variant', ['officialDebug']), ('format', ['apk'])]:
            require(inputs[name]['options'] == expected and inputs[name]['required'] is True,
                    f'dispatch input {name} changed')
    print('MMRL Devtool GitHub Actions contract: PASS (offline)')


def command(args: list[str]) -> str:
    r = subprocess.run(args, capture_output=True, text=True, timeout=35)
    require(r.returncode == 0, f'command failed: {Path(args[0]).name}')
    return r.stdout + '\n' + r.stderr


def evidence(directory: Path) -> None:
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and
            os.environ.get('GITHUB_EVENT_NAME') == 'workflow_dispatch',
            'package evidence can only be produced inside GitHub workflow_dispatch')
    sha = os.environ.get('GITHUB_SHA', '')
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    attempt = os.environ.get('GITHUB_RUN_ATTEMPT', '')
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    require(re.fullmatch(r'[0-9a-f]{40}', sha) is not None, 'invalid full GitHub SHA')
    require(run_id.isdecimal() and attempt.isdecimal() and int(run_id) > 0 and int(attempt) > 0,
            'invalid GitHub run identity')
    require(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo) is not None,
            'invalid GitHub repository identity')
    require(command(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).strip() == sha,
            'checked out SHA does not match dispatched SHA')
    out_root = ROOT / 'app/build/outputs/apk/official/debug'
    require(out_root.is_dir() and not out_root.is_symlink(), 'OfficialDebug output directory absent')
    candidates = [p for p in out_root.glob('*.apk') if p.is_file() and not p.is_symlink()]
    require(len(candidates) == 1 and candidates[0].stat().st_size > 0,
            f'exactly one nonempty OfficialDebug APK required; found {len(candidates)}')
    apk = candidates[0]
    sdk = Path(os.environ.get('ANDROID_HOME', '/not-set')) / 'build-tools' / '36.0.0'
    sig = command([str(sdk / 'apksigner'), 'verify', '--print-certs', str(apk)])
    fingerprints = re.findall(r'(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$', sig)
    require(len(fingerprints) == 1, 'unexpected/missing signer certificate fingerprint')
    badging = command([str(sdk / 'aapt'), 'dump', 'badging', str(apk)])
    require(re.search(r"(?m)^package: name='" + re.escape(PACKAGE) + r"'\s", badging) is not None,
            f'APK package does not match {PACKAGE}')
    require(directory.is_dir() and not directory.is_symlink(), 'artifact directory missing')
    staged = directory / STAGED_APK
    receipt_path = directory / 'build-evidence.json'
    require(not staged.exists() and not receipt_path.exists(), 'refusing to overwrite evidence')
    with apk.open('rb') as fh:
        digest = hashlib.file_digest(fh, 'sha256').hexdigest()
    import shutil
    shutil.copyfile(apk, staged)
    receipt = {
        'schema_version': 1, 'producer': 'mmrl-devtool-official-debug',
        'repository': repo, 'source_sha': sha,
        'github_run_id': int(run_id), 'github_run_attempt': int(attempt),
        'workflow_event': 'workflow_dispatch', 'module': 'app',
        'variant': 'officialDebug', 'format': 'apk', 'package': PACKAGE,
        'original_apk_filename': apk.name, 'artifact_member': STAGED_APK,
        'apk_sha256': digest, 'apk_size_bytes': apk.stat().st_size,
        'signing_certificate_sha256': fingerprints[0].lower(),
        'signature_verified': True, 'manifest_verified': True,
        'static_unit_lint_and_instrumentation_compile_prerequisites': True,
        'device_tests_executed': False, 'device_qualified': False,
        'production_signed': False, 'release_qualified': False,
    }
    with receipt_path.open('x', encoding='utf-8') as f:
        json.dump(receipt, f, indent=2, sort_keys=True)
        f.write('\n')
    print(f'MMRL source-bound debug candidate: {staged.name}, SHA256={digest}')


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--static', action='store_true')
    mode.add_argument('--evidence', metavar='OUTPUT_DIRECTORY')
    args = parser.parse_args()
    try:
        static()
        if args.evidence:
            evidence(Path(args.evidence).resolve())
    except (ValueError, OSError, subprocess.TimeoutExpired, KeyError) as exc:
        print(f'MMRL GitHub Actions: FAIL: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
