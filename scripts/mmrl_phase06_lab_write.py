#!/usr/bin/env python3
"""R8 opt-in Phase06 manager write acceptance, restricted to Phase05 disposable lab.

Never changes unrelated module state, system packages, or broad scopes. Writes to
Vector occur only in the explicit Android instrumentation and, on failure, in
this host's exact-target recovery path. Retains source-bound evidence and a
pre-write, fsynced recovery journal outside Android app storage.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import secrets
import shlex
from pathlib import Path
import re
import subprocess
import sys
import time

import mmrl_phase05_device as lab

REPO = 'Mikeyphw/MMRL'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'
APP_PREFIX = 'com.dergoogler.mmrl.phase06'
LAB_TARGET = lab.TARGET
LAB_MODULE = lab.MODULE
EXPECTED_SCOPE = LAB_TARGET + '/0'
CONSENT = 'AUTHORIZE_DISPOSABLE_LAB_ONLY'


def run(*parts, timeout=180):
    return subprocess.run(parts, capture_output=True, text=True, timeout=timeout)


def checked(*parts, timeout=180):
    result = run(*parts, timeout=timeout)
    if result.returncode:
        raise RuntimeError(parts[0] + ' returned nonzero (' + str(result.returncode) + '): ' +
                           (result.stderr + result.stdout)[-300:])
    return result.stdout.strip()


def adb(serial, *parts):
    return checked('adb', '-s', serial, *parts)


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def persist(path, doc, *, create=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = (json.dumps(doc, sort_keys=True, indent=2) + '\n').encode()
    if create:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as f:
            f.write(payload)
            f.flush()
            os.fsync(f.fileno())
    else:
        tmp = path.with_suffix('.json.pending')
        with tmp.open('xb') as f:
            f.write(payload)
            f.flush()
            os.fsync(f.fileno())
        tmp.replace(path)
    fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def check_github(run_id):
    run_info = json.loads(checked('gh', 'api', f'repos/{REPO}/actions/runs/{run_id}'))
    if (run_info.get('status') != 'completed' or run_info.get('conclusion') != 'success'
            or run_info.get('head_branch') != 'feature/lsposed-phase06-vector-manager'):
        raise RuntimeError('GitHub Phase06 run is not successful on the expected branch')
    attempt = int(run_info['run_attempt'])
    if attempt < 1 or not re.fullmatch('[0-9a-f]{40}', run_info['head_sha']):
        raise RuntimeError('Invalid hosted run identity')
    return run_info, attempt


def download_verified(run_id, attempt, head_sha, parent):
    appdir = parent/'mmrl-apks'
    labdir = parent/'lab-apks'
    for directory, artifact in (
        (appdir, f'mmrl-phase06-isolated-{run_id}-attempt{attempt}'),
        (labdir, f'mmrl-lsposed-hook-lab-{run_id}-attempt{attempt}'),
    ):
        directory.mkdir(parents=True, exist_ok=False)
        checked('gh', 'run', 'download', str(run_id), '-R', REPO, '-n', artifact, '-D', str(directory))
    app = appdir/'MMRL-Phase06-Isolated-App.apk'
    test = appdir/'MMRL-Phase06-Isolated-Test.apk'
    target = labdir/'MMRL-HookLab-Target-debug.apk'
    module = labdir/'MMRL-HookLab-Module-debug.apk'
    for file in (app, test, target, module):
        if not file.is_file():
            raise RuntimeError('Missing attested APK: ' + file.name)
    r1 = json.loads((appdir/'pair-receipt.json').read_text())
    r2 = json.loads((labdir/'pair-receipt.json').read_text())
    app_pkg = f'{APP_PREFIX}.r{run_id}a{attempt}'
    app_required = {
        'schema': 'mmrl.lsposed.phase06.isolated-apk-pair.v1',
        'run_id': run_id, 'attempt': attempt,
        'main_package': app_pkg, 'test_package': app_pkg+'.test',
        'main_sha256': sha256(app), 'test_sha256': sha256(test),
        'head_sha': head_sha, 'runner': RUNNER, 'device_qualified': False,
    }
    lab_required = {
        'schema': 'mmrl.lsposed.hook-lab-pair.v1',
        'status': 'VERIFIED_DEBUG_PAIR_NOT_DEVICE_QUALIFIED',
        'run_id': run_id, 'attempt': attempt, 'head_sha': head_sha,
        'target_package': LAB_TARGET, 'module_package': LAB_MODULE,
        'target_sha256': sha256(target), 'module_sha256': sha256(module),
        'static_scope': [LAB_TARGET], 'modern_api': 102,
        'device_qualified': False, 'hooks_verified': False, 'injection_verified': False,
    }
    if any(r1.get(k) != v for k, v in app_required.items()):
        raise RuntimeError('Phase06 app pair receipt mismatch')
    if any(r2.get(k) != v for k, v in lab_required.items()):
        raise RuntimeError('Hook-lab pair receipt mismatch')
    if r1.get('source_commit') != r2.get('source_commit') or not re.fullmatch(
            '[0-9a-f]{40}', r1.get('source_commit', '')):
        raise RuntimeError('GitHub source identity differs between pairs')
    # Different GitHub build jobs may have different debug signing certificates.
    # Only the matching, independently attested APK pair receipts are trusted.
    return app_pkg, {app_pkg: app, app_pkg+'.test': test,
                     LAB_TARGET: target, LAB_MODULE: module}, r1


def installed_apk(serial, package, destination):
    # The Android package manager may exit nonzero for `pm path` on an absent
    # package. Check exact user-0 package presence first. A missing disposable
    # hook-lab package is permitted; an unexpected ADB error is NOT.
    installed = adb(serial, 'shell', 'pm', 'list', 'packages', '--user', '0', package).splitlines()
    if f'package:{package}' not in installed:
        return False
    lines = adb(serial, 'shell', 'pm', 'path', '--user', '0', package).splitlines()
    if len(lines) != 1 or not lines[0].startswith('package:/data/app/') or not lines[0].endswith('.apk'):
        raise RuntimeError('Cannot hash exact single installed APK for ' + package)
    path = lines[0].removeprefix('package:')
    try:
        checked('adb', '-s', serial, 'pull', path, str(destination), timeout=90)
    except RuntimeError as exc:
        raise RuntimeError('Cannot pull exact installed APK for ' + package + ': ' + str(exc)) from exc
    if not destination.is_file():
        raise RuntimeError('ADB could not retrieve installed package for hashing: ' + package)
    return True


def ensure_pair(serial, artifacts, out, *, install_lab):
    hashes = {}
    missing = []
    # First verify ALL existing packages before even installing a missing lab
    # APK. Never quietly upgrade a mismatched pre-existing test package.
    for pkg, apk in artifacts.items():
        path = out / ('installed-' + pkg + '.apk')
        if not installed_apk(serial, pkg, path):
            if not install_lab or pkg not in (LAB_TARGET, LAB_MODULE):
                raise RuntimeError('Missing already-installed isolated APK: ' + pkg)
            missing.append(pkg)
            continue
        if sha256(path) != sha256(apk):
            raise RuntimeError('Installed APK differs from exact GitHub artifact: ' + pkg)
        hashes[pkg] = sha256(path)
    for pkg in missing:
        apk = artifacts[pkg]
        path = out / ('installed-' + pkg + '.apk')
        # No -r: never replace a pre-existing app. Only the two test-only IDs.
        adb(serial, 'install', str(apk))
        if not installed_apk(serial, pkg, path) or sha256(path) != sha256(apk):
            raise RuntimeError('Lab install does not match exact attested APK: ' + pkg)
        hashes[pkg] = sha256(path)
    return hashes


def lab_state(serial):
    status = lab.cli(serial, 'status')
    if not isinstance(status, dict) or int(status.get('API Version', 0)) < 102:
        raise RuntimeError('Vector API 102+ required')
    enabled = lab.modules(serial)
    scopes = lab.scope(serial)
    if enabled != 'disabled' or scopes not in ([], [EXPECTED_SCOPE]):
        raise RuntimeError('Lab baseline must be disabled with no unrelated scope; no writes allowed')
    return scopes


def recover(serial, journal_path, journal):
    if (journal.get('schema') != 'mmrl.lsposed.phase06.lab-write-host.v1'
            or journal.get('serial') != serial or journal.get('original_enabled') is not False
            or journal.get('original_scope') not in ([], [EXPECTED_SCOPE])
            or journal.get('module_package') != LAB_MODULE
            or journal.get('target_package') != LAB_TARGET):
        raise RuntimeError('Recovery journal does not authorize a lab-only restoration')
    journal['phase'] = 'HOST_RECOVERY_STARTED'
    persist(journal_path, journal)
    now = lab.modules(serial)
    scopes = lab.scope(serial)
    if now not in ('enabled', 'disabled') or scopes not in ([], [EXPECTED_SCOPE]):
        raise RuntimeError('Lab state or scope is unexpected; refusing unsafe restoration')
    # A scope ADD implicitly enables the module in Vector v2.2. Restore scope
    # first; ALWAYS perform final explicit disable and independently verify it.
    if scopes != journal['original_scope']:
        if journal['original_scope'] == []:
            lab.cli(serial, 'scope', 'rm', LAB_MODULE, EXPECTED_SCOPE)
        else:
            lab.cli(serial, 'scope', 'add', LAB_MODULE, EXPECTED_SCOPE)
    if lab.modules(serial) == 'enabled':
        lab.cli(serial, 'modules', 'disable', LAB_MODULE)
        if lab.modules(serial) != 'disabled':
            raise RuntimeError('Lab module disable was not acknowledged')
    restored = lab.modules(serial) == 'disabled' and lab.scope(serial) == journal['original_scope']
    if not restored:
        raise RuntimeError('Lab baseline did not restore')
    journal['phase'] = 'HOST_RESTORED'
    journal['restored'] = True
    persist(journal_path, journal)
    return True


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('run_id', type=int)
    p.add_argument('serial')
    p.add_argument('--authorize-lab-changes', action='store_true')
    p.add_argument('--recover', type=Path, help='Recover ONLY the previously journaled lab configuration')
    p.add_argument('--out', type=Path, default=Path.home()/'.local/state/mmrl-phase06-r8')
    a = p.parse_args()
    if not a.authorize_lab_changes:
        raise RuntimeError('Explicit --authorize-lab-changes flag required')
    props = lab.verify_device(a.serial)
    if a.recover:
        previous = json.loads(a.recover.read_text())
        if previous.get('run_id') != a.run_id:
            raise RuntimeError('Recovery run ID differs from host journal')
        recover(a.serial, a.recover, previous)
        print('HOST_RESTORED: only the disposable lab configuration was restored')
        return
    run_info, attempt = check_github(a.run_id)
    output = a.out/f'run-{a.run_id}-attempt{attempt}'/a.serial
    output.mkdir(parents=True, exist_ok=False)
    app, artifacts, receipt = download_verified(a.run_id, attempt, run_info['head_sha'], output)
    # Check existing Vector state before any lab APK installation.
    lab.cli(a.serial, 'status')
    # If lab is already present, never touch an enabled or unexpected-scope lab.
    current = lab.cli(a.serial, 'modules', 'ls')
    rows = [x for x in current if isinstance(x, dict) and x.get('PACKAGE') == LAB_MODULE]
    if len(rows) > 1 or (rows and rows[0].get('STATUS') != 'disabled'):
        raise RuntimeError('Existing lab module must be disabled before APK checks')
    if rows and lab.scope(a.serial) not in ([], [EXPECTED_SCOPE]):
        raise RuntimeError('Existing lab scope contains other targets; refusing')
    hashes = ensure_pair(a.serial, artifacts, output, install_lab=True)
    registered = adb(a.serial, 'shell', 'pm', 'list', 'instrumentation')
    if f'{app}.test/{RUNNER} (target={app})' not in registered:
        raise RuntimeError('Installed Phase06 test package is not registered for exact target')
    original_scope = lab_state(a.serial)
    prior_readonly = json.loads(adb(a.serial, 'exec-out', 'run-as', app,
                                    'cat', 'files/mmrl_vector_manager_device.json'))
    if not (prior_readonly.get('status') == 'PASS_READ_ONLY_INVENTORY'
            and prior_readonly.get('target_package') == app
            and prior_readonly.get('read_only') is True
            and prior_readonly.get('module_writes') == 0
            and prior_readonly.get('scope_writes') == 0):
        raise RuntimeError('Read-only Phase06 acceptance must first pass on the exact installed pair')
    journal_path = output/'journal.json'
    journal = {
        'schema': 'mmrl.lsposed.phase06.lab-write-host.v1',
        'run_id': a.run_id, 'attempt': attempt, 'serial': a.serial,
        'source_commit': receipt['source_commit'], 'head_sha': run_info['head_sha'],
        'module_package': LAB_MODULE, 'target_package': LAB_TARGET,
        'isolated_app_package': app, 'original_enabled': False,
        'original_scope': original_scope, 'device': props, 'installed_apk_sha256': hashes,
        'phase': 'PRE_WRITE_JOURNALED', 'restored': False,
    }
    persist(journal_path, journal, create=True)
    print('Pre-write recovery journal: ' + str(journal_path), flush=True)
    # A one-time authorization file is committed to the isolated app only AFTER
    # the host recovery journal exists. Direct instrumentation without this
    # ticket cannot exercise Vector writes.
    nonce = secrets.token_hex(24)
    ticket = f'{app}:{nonce}:{LAB_MODULE}:{LAB_TARGET}'
    ticket_command = 'umask 077; printf %s ' + shlex.quote(ticket) +                      ' > files/mmrl_vector_manager_lab_authorization.token'
    adb(a.serial, 'shell', f'run-as {app} sh -c {shlex.quote(ticket_command)}')
    echoed = adb(a.serial, 'exec-out', 'run-as', app,
                 'cat', 'files/mmrl_vector_manager_lab_authorization.token')
    if echoed != ticket:
        raise RuntimeError('Host authorization ticket was not committed')
    journal['phase'] = 'HOST_TICKET_COMMITTED'
    persist(journal_path, journal)
    try:
        instrumentation = run('adb', '-s', a.serial, 'shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', 'com.dergoogler.mmrl.lsposed.VectorManagerLabDeviceTest',
            '-e', 'mmrl_phase06_lab_authorization', CONSENT,
            '-e', 'mmrl_phase06_lab_nonce', nonce,
            f'{app}.test/{RUNNER}', timeout=300)
    except subprocess.TimeoutExpired as timed_out:
        # Never recover concurrently with a still-running test. Stop ONLY the
        # run-unique MMRL app, then allow Vector's bounded CLI child to exit.
        try:
            adb(a.serial, 'shell', 'am', 'force-stop', app)
            time.sleep(15)
        except Exception as stop_exc:
            journal['phase'] = 'RECOVERY_REQUIRED'
            journal['recovery_error'] = ('Timed-out instrumentation could not be stopped: ' + str(stop_exc))[:220]
            persist(journal_path, journal)
            raise RuntimeError('Timed-out test may still be running; do not recover concurrently')
        instrumentation = subprocess.CompletedProcess(
            args=['adb', 'am', 'instrument'], returncode=124,
            stdout=(timed_out.stdout or b'').decode(errors='replace') if isinstance(timed_out.stdout, bytes) else (timed_out.stdout or ''),
            stderr='Instrumentation timeout; isolated test app stopped before host restoration')
    (output/'instrumentation.log').write_text(instrumentation.stdout + '\n' + instrumentation.stderr)
    journal['phase'] = 'INSTRUMENTATION_RETURNED'
    persist(journal_path, journal)
    error = None
    try:
        device = json.loads(adb(a.serial, 'exec-out', 'run-as', app,
                                'cat', 'files/mmrl_vector_manager_lab_write.json'))
        (output/'lab-device.json').write_text(json.dumps(device, indent=2, sort_keys=True) + '\n')
    except Exception as exc:
        device = {'status': 'MISSING_DEVICE_EVIDENCE'}
        error = str(exc)
    try:
        recover(a.serial, journal_path, journal)
        restoration = True
    except Exception as exc:
        restoration = False
        journal['phase'] = 'RECOVERY_REQUIRED'
        journal['recovery_error'] = str(exc)[:220]
        persist(journal_path, journal)
        error = (error or '') + '; recovery: ' + str(exc)
    qualified = (instrumentation.returncode == 0 and 'OK (1 test)' in instrumentation.stdout
                 and device.get('status') == 'PASS_LAB_WRITE_AND_RESTORE'
                 and device.get('target_package') == app
                 and device.get('module_package') == LAB_MODULE
                 and device.get('scope_target') == EXPECTED_SCOPE
                 and device.get('scope_writes_attempted') == 2
                 and device.get('module_writes_attempted') == 2
                 and device.get('restored') is True
                 and restoration)
    result = {'schema': 'mmrl.lsposed.phase06.lab-write-host.v1',
              'run_id': a.run_id, 'source_commit': receipt['source_commit'],
              'app_package': app, 'device': props,
              'device_status': device.get('status'), 'restored': restoration,
              'module_writes_attempted': device.get('module_writes_attempted'),
              'scope_writes_attempted': device.get('scope_writes_attempted'),
              'hooks_verified': False, 'injection_verified': False,
              'qualification': 'PASS_LAB_WRITE_AND_RESTORATION' if qualified else
              ('RECOVERY_REQUIRED' if not restoration else 'NOT_QUALIFIED'),
              'error': error}
    (output/'host-result.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print(json.dumps(result, indent=2, sort_keys=True))
    print('Evidence: ' + str(output))
    if not qualified:
        raise RuntimeError('Lab write acceptance incomplete; inspect host journal and results')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        print('MMRL_PHASE06_R8_REFUSED: ' + str(exc), file=sys.stderr)
        sys.exit(2)
