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
APP = 'com.dergoogler.mmrl.debug'
TEST = 'com.dergoogler.mmrl.debug.test'
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
    destination = a.out/f'run-{a.run_id}-attempt{attempt}'/a.serial
    destination.mkdir(parents=True, exist_ok=False)
    app_dir = destination/'app'
    test_dir = destination/'test'
    for path, name in ((app_dir, f'mmrl-lsposed-officialdebug-{a.run_id}-attempt{attempt}'),
                       (test_dir, f'mmrl-lsposed-device-test-apk-{a.run_id}-attempt{attempt}')):
        path.mkdir()
        checked('gh', 'run', 'download', str(a.run_id), '-R', REPO, '-n', name, '-D', str(path))
    app = sole_apk(app_dir)
    tests = sole_apk(test_dir)
    receipt = json.loads((test_dir/'pair-receipt.json').read_text())
    expected = {'schema':'mmrl.lsposed.device-apk-pair.v1', 'run_id':a.run_id, 'attempt':attempt,
                'main_package':APP, 'test_package':TEST, 'runner':RUNNER,
                'main_sha256':sha(app), 'test_sha256':sha(tests), 'device_qualified':False}
    if any(receipt.get(k) != v for k,v in expected.items()):
        raise RuntimeError('APK pair receipt mismatch')
    if receipt.get('source_commit') != github['head_sha']:
        # PR workflow uses a synthetic test-merge commit; preserve and record it instead.
        if not re.fullmatch('[a-f0-9]{40}', receipt.get('source_commit', '')):
            raise RuntimeError('Source provenance missing')
    adb(a.serial, 'install', '-r', str(app))
    adb(a.serial, 'install', '-r', str(tests))
    registered = adb(a.serial, 'shell', 'pm', 'list', 'instrumentation')
    if f'{TEST}/{RUNNER} (target={APP})' not in registered:
        raise RuntimeError('Instrumentation target mismatch')
    p = run('adb', '-s', a.serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'com.dergoogler.mmrl.lsposed.VectorManagerDeviceTest', f'{TEST}/{RUNNER}', timeout=240)
    (destination/'instrumentation.log').write_text(p.stdout+'\n'+p.stderr)
    evidence = json.loads(adb(a.serial, 'exec-out', 'run-as', APP, 'cat', 'files/mmrl_vector_manager_device.json'))
    (destination/'manager-readonly.json').write_text(json.dumps(evidence, indent=2, sort_keys=True)+'\n')
    result = {'schema':'mmrl.lsposed.phase06.device-host.v1', 'run_id':a.run_id,
              'source_commit':receipt['source_commit'], 'device':props, 'evidence':evidence,
              'instrumentation_completed':p.returncode == 0 and 'OK (1 test)' in p.stdout,
              'module_writes':0, 'scope_writes':0, 'hooks_verified':False,
              'qualification':'NOT_QUALIFIED'}
    if result['instrumentation_completed'] and evidence.get('status') == 'PASS_READ_ONLY_INVENTORY':
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
