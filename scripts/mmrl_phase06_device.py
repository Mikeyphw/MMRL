#!/usr/bin/env python3
"""Physical-device, read-only Vector manager instrumentation. Requires paired CI APKs."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = 'Mikeyphw/MMRL'
APP_PREFIX = 'com.dergoogler.mmrl.phase06'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'


def run(*args, timeout=180):
    return subprocess.run(args, text=True, capture_output=True, timeout=timeout)


def checked(*args, timeout=180):
    p = run(*args, timeout=timeout)
    if p.returncode:
        raise RuntimeError(f'{args[0]} exit {p.returncode}: {p.stdout[-200:]} {p.stderr[-200:]}')
    return p.stdout.strip()


def adb(serial, *args):
    return checked('adb', '-s', serial, *args)


def sha(file):
    h = hashlib.sha256()
    with file.open('rb') as f:
        for block in iter(lambda: f.read(1048576), b''):
            h.update(block)
    return h.hexdigest()


def sole_apk(directory):
    candidates = list(directory.rglob('*.apk'))
    if len(candidates) != 1:
        raise RuntimeError('Expected exactly one APK in '+str(directory))
    return candidates[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run_id', type=int)
    parser.add_argument('serial', help='Physical adb serial, e.g. localhost:5555')
    parser.add_argument('--out', type=Path, default=Path.home()/'.local/state/mmrl-phase06')
    a = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._:-]{1,100}', a.serial):
        raise RuntimeError('Explicit valid ADB serial required')
    if adb(a.serial, 'get-state') != 'device':
        raise RuntimeError('ADB device unavailable')
    props = {k: adb(a.serial, 'shell', 'getprop', k) for k in
             ('ro.kernel.qemu', 'ro.hardware', 'ro.build.characteristics', 'ro.product.model')}
    if props['ro.kernel.qemu'] == '1' or props['ro.hardware'] in ('goldfish', 'ranchu') or 'emulator' in props['ro.build.characteristics'].lower():
        raise RuntimeError('Physical Android device required')
    github = json.loads(checked('gh', 'api', f'repos/{REPO}/actions/runs/{a.run_id}'))
    if (github.get('status') != 'completed' or github.get('conclusion') != 'success'
            or github.get('head_branch') not in ('feature/lsposed-phase06-vector-manager', 'lsposed-vector')):
        raise RuntimeError('Exact Phase06 hosted run has not passed')
    attempt = int(github.get('run_attempt', 0))
    if attempt < 1:
        raise RuntimeError('Invalid GitHub run attempt')
    app_id = f'{APP_PREFIX}.r{a.run_id}a{attempt}'
    test_id = app_id + '.test'
    destination = a.out/f'run-{a.run_id}-attempt{attempt}'/a.serial
    destination.mkdir(parents=True, exist_ok=False)
    app_dir = destination/'app'
    test_dir = destination/'test'
    # Never reuse the ordinary MMRL APK. Both APKs are a distinct, run-unique test installation.
    app_dir.mkdir()
    checked('gh', 'run', 'download', str(a.run_id), '-R', REPO,
            '-n', f'mmrl-phase06-isolated-{a.run_id}-attempt{attempt}', '-D', str(app_dir))
    test_dir = app_dir
    app = app_dir/'MMRL-Phase06-Isolated-App.apk'
    tests = app_dir/'MMRL-Phase06-Isolated-Test.apk'
    if not app.is_file() or not tests.is_file():
        raise RuntimeError('Isolated signed app/test APKs missing')
    receipt = json.loads((test_dir/'pair-receipt.json').read_text())
    expected = {'schema':'mmrl.lsposed.phase06.isolated-apk-pair.v1',
                'run_id':a.run_id, 'attempt':attempt,
                'main_package':app_id, 'test_package':test_id, 'runner':RUNNER,
                'main_sha256':sha(app), 'test_sha256':sha(tests),
                'head_sha':github['head_sha'], 'device_qualified':False}
    if any(receipt.get(k) != v for k,v in expected.items()):
        raise RuntimeError('APK pair receipt mismatch')
    if not re.fullmatch('[a-f0-9]{40}', receipt.get('source_commit', '')):
        raise RuntimeError('Missing CI checkout source identity')
    # Never replace another installation, including an earlier isolated run.
    for package in (app_id, test_id):
        installed = adb(a.serial, 'shell', 'pm', 'list', 'packages', '--user', '0', package)
        if f'package:{package}' in installed.splitlines():
            raise RuntimeError('Isolated package already installed; refusing overwrite: ' + package)
    adb(a.serial, 'install', str(app))
    adb(a.serial, 'install', str(tests))
    registered = adb(a.serial, 'shell', 'pm', 'list', 'instrumentation')
    if f'{test_id}/{RUNNER} (target={app_id})' not in registered:
        raise RuntimeError('Instrumentation target mismatch')
    p = run('adb', '-s', a.serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'com.dergoogler.mmrl.lsposed.VectorManagerDeviceTest', f'{test_id}/{RUNNER}', timeout=240)
    (destination/'instrumentation.log').write_text(p.stdout+'\n'+p.stderr)
    evidence = json.loads(adb(a.serial, 'exec-out', 'run-as', app_id, 'cat', 'files/mmrl_vector_manager_device.json'))
    (destination/'manager-readonly.json').write_text(json.dumps(evidence, indent=2, sort_keys=True)+'\n')
    result = {'schema':'mmrl.lsposed.phase06.device-host.v1', 'run_id':a.run_id,
              'isolated_app_package':app_id, 'isolated_test_package':test_id,
              'source_commit':receipt['source_commit'], 'device':props, 'evidence':evidence,
              'instrumentation_completed':p.returncode == 0 and 'OK (1 test)' in p.stdout,
              'module_writes':0, 'scope_writes':0, 'hooks_verified':False,
              'qualification':'NOT_QUALIFIED'}
    if (result['instrumentation_completed'] and evidence.get('status') == 'PASS_READ_ONLY_INVENTORY'
            and evidence.get('target_package') == app_id
            and evidence.get('read_only') is True
            and evidence.get('module_writes') == 0 and evidence.get('scope_writes') == 0):
        result['qualification'] = 'PASS_READ_ONLY_MANAGER_CONFIGURATION'
    (destination/'host-result.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print(json.dumps(result,indent=2,sort_keys=True))
    print('Evidence: '+str(destination))
    if result['qualification'] != 'PASS_READ_ONLY_MANAGER_CONFIGURATION':
        raise RuntimeError('Manager read-only device acceptance incomplete')
    print('PASS: MMRL Vector manager configuration inventory on physical Android')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print('MMRL_PHASE06_REFUSED: '+str(error), file=sys.stderr)
        sys.exit(2)
