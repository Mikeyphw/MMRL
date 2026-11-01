#!/usr/bin/env python3
"""Offline, no-Gradle R36 MMRL consumer source-admission checks.

Uses Python's standard library plus the *installed* Devtool being upgraded.
"""
from __future__ import annotations

import ast
import hashlib
import importlib.util
from pathlib import Path
import re
import subprocess
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]

def require(ok: bool, reason: str) -> None:
    if not ok:
        raise AssertionError(reason)


def main() -> None:
    spec = importlib.util.spec_from_file_location('mmrl_r36_hosted', ROOT / 'scripts/mmrl_r36_hosted.py')
    app = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(app)
    ast.parse((ROOT / 'scripts/mmrl_r36_hosted.py').read_text())
    graph = app.checked_graph(ROOT)
    node = graph['jobs'][0]
    provider = node['android_review']
    require(node['id'] == 'node-01', 'node ID')
    require(provider['semantic_request'] is True, 'semantic request')
    require(provider['project_dir'] == '' and provider['variant'] == 'officialDebug', 'officialDebug root/variant')
    require(provider['tasks'] == [':app:lintOfficialDebug', ':app:assembleOfficialDebug'], 'exact tasks')
    require(node['outputs'] == [app.OUTPUT], 'APK output')
    require(provider['wrapper_version'] == '9.7.1', 'wrapper version')
    jar = (ROOT / 'gradle/wrapper/gradle-wrapper.jar').read_bytes()
    require(provider['wrapper_jar_sha256'] == hashlib.sha256(jar).hexdigest(), 'wrapper hash')
    require(not provider['release_qualified'] and not provider['signer_authority'] and not provider['device_qualified'], 'qualification exclusion')
    cfg = tomllib.loads((ROOT / '.devtool.toml').read_text())
    require(cfg['workspace']['default_target'] == 'mmrl_android', 'default target preserved')
    release = cfg['targets']['mmrl_android']['android']['release']
    require(release['device_tests'] == 'required' and release['verify_certificate_identity'], 'release identity preserved')
    signing = cfg['targets']['mmrl_android']['android']['signing']['release']
    require(signing['mode'] == 'project' and signing['policy'] == 'release-required', 'release signing unchanged')
    workflow = (ROOT / '.github/workflows/devtool-mmrl-r36-hosted-official-debug.yml').read_text()
    require('workflow_dispatch:' in workflow, 'dispatch only')
    require('  push:' not in workflow and '  pull_request:' not in workflow, 'no automatic admission')
    require('ORG_GRADLE_PROJECT_mmrlR36HostedDebug: \'true\'' in workflow, 'hosted opt-in')
    require('scripts/mmrl_r36_hosted.py run' in workflow, 'native DAG runner')
    require('scripts/mmrl_r36_hosted.py verify' in workflow, 'signature verifier')
    require('git clone --no-checkout https://github.com/Mikeyphw/devtool.git' in workflow, 'pinned runtime')
    require('secrets.' not in workflow and ':app:assembleOfficialRelease' not in workflow, 'no signing release')
    require(not re.search(r'(?m)^\s*\./gradlew\s', workflow), 'no direct Gradle runner')
    for path in ('.github/workflows/devtool-android-build.yml', '.github/workflows/mmrl-release-seal.yml'):
        require((ROOT/path).is_file(), 'existing workflow removed')
    env = dict(__import__('os').environ)
    env.pop('GITHUB_ACTIONS', None)
    # This must run fully offline and refuse local Gradle, regardless of env defaults.
    run = subprocess.run([sys.executable, str(ROOT/'scripts/mmrl_r36_hosted.py'), 'run', '--root', str(ROOT),
                          '--inputs', '/nonexistent/mmrl-r36-in', '--outputs', '/nonexistent/mmrl-r36-out'],
                         capture_output=True, text=True, env=env, timeout=25)
    require(run.returncode == 2 and 'MMRL_R36_REFUSED' in run.stderr, 'must block local execution')
    print('PASS_MMRL_R36_NAMED_VARIANT_NATIVE_HOSTED_CONTRACT_NO_GRADLE')


if __name__ == '__main__':
    main()
