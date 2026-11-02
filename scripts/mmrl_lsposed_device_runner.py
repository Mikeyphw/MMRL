#!/usr/bin/env python3
"""Local adb runner: exact GitHub APK pair, read-only physical-device evidence."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

REPO = 'Mikeyphw/MMRL'
APP_PKG = 'com.dergoogler.mmrl.debug'
TEST_PKG = 'com.dergoogler.mmrl.debug.test'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'


def command(*args, capture=True):
    return subprocess.run(args, check=True, capture_output=capture, text=True)


def digest(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for data in iter(lambda: f.read(1024 * 1024), b''):
            h.update(data)
    return h.hexdigest()


def apk_at(directory):
    found = [p for p in directory.rglob('*.apk') if p.is_file()]
    if len(found) != 1:
        raise RuntimeError('Expected one APK in '+str(directory)+', found '+str(len(found)))
    return found[0]


def adb(serial, *args):
    return command('adb', '-s', serial, *args).stdout.strip()


def download(run, name, location):
    location.mkdir(parents=True, exist_ok=False)
    command('gh', 'run', 'download', str(run), '-R', REPO,
            '-n', name, '-D', str(location))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('run_id', type=int, help='Successful GitHub Actions CI run id')
    p.add_argument('device_serial', help='Explicit physical adb device serial')
    p.add_argument('--out', type=Path, default=Path.home()/'.local/state/mmrl-lsposed-device')
    a = p.parse_args()
    if a.run_id < 1 or not a.device_serial.strip() or a.device_serial.startswith('-'):
        raise RuntimeError('A real GitHub run and explicit device serial are mandatory')
    if not re.fullmatch(r'[a-zA-Z0-9._:-]+', a.device_serial):
        raise RuntimeError('Invalid adb device serial')
    if adb(a.device_serial, 'get-state') != 'device':
        raise RuntimeError('Device is not accessible through adb')
    props = {key: adb(a.device_serial, 'shell', 'getprop', key)
             for key in ('ro.kernel.qemu', 'ro.hardware', 'ro.product.model', 'ro.build.characteristics', 'ro.build.version.sdk')}
    if (props['ro.kernel.qemu'] == '1' or
        props['ro.hardware'].lower() in ('goldfish', 'ranchu') or
        'emulator' in props['ro.build.characteristics'].lower() or
        a.device_serial.startswith('emulator-')):
        raise RuntimeError('REFUSED: selected adb device is an emulator, not a real phone')
    run = json.loads(command('gh', 'api', f'repos/{REPO}/actions/runs/{a.run_id}').stdout)
    if run['status'] != 'completed' or run['conclusion'] != 'success':
        raise RuntimeError('Exact GitHub run has not passed')
    if run.get('head_branch') != 'feature/lsposed-device-acceptance':
        raise RuntimeError('Refusing unrelated GitHub run branch: '+str(run.get('head_branch')))
    attempt = int(run.get('run_attempt', 1))
    output = a.out / f'run-{a.run_id}-attempt{attempt}' / a.device_serial
    if output.exists():
        raise RuntimeError('Evidence directory already exists; runs are immutable: '+str(output))
    output.mkdir(parents=True)
    app_dir = output/'github-app'
    test_dir = output/'github-test'
    download(a.run_id, f'mmrl-lsposed-officialdebug-{a.run_id}-attempt{attempt}', app_dir)
    download(a.run_id, f'mmrl-lsposed-device-test-apk-{a.run_id}-attempt{attempt}', test_dir)
    app_apk = apk_at(app_dir)
    test_apk = apk_at(test_dir)
    receipt_path = test_dir/'pair-receipt.json'
    receipt = json.loads(receipt_path.read_text())
    expected = {
        'schema': 'mmrl.lsposed.device-apk-pair.v1',
        'run_id': a.run_id, 'attempt': attempt,
        'main_package': APP_PKG, 'test_package': TEST_PKG, 'runner': RUNNER,
        'main_sha256': digest(app_apk), 'test_sha256': digest(test_apk),
        'device_qualified': False,
    }
    if any(receipt.get(k) != value for k, value in expected.items()):
        raise RuntimeError('Refusing APKs: mismatch with GitHub build pair receipt')
    print(f'READ-ONLY DEVICE ACCEPTANCE: model={props["ro.product.model"]} run={a.run_id} SHA={receipt["source_commit"]}')
    print('Installing signed debug MMRL and matching instrumentation APK. No uninstall or data clear.')
    adb(a.device_serial, 'install', '-r', str(app_apk))
    adb(a.device_serial, 'install', '-r', str(test_apk))
    registered = adb(a.device_serial, 'shell', 'pm', 'list', 'instrumentation')
    if f'{TEST_PKG}/{RUNNER} (target={APP_PKG})' not in registered:
        raise RuntimeError('Installed instrumentation target does not match MMRL debug package')

    result = subprocess.run(['adb', '-s', a.device_serial, 'shell', 'am', 'instrument', '-w', '-r',
          '-e', 'class', 'com.dergoogler.mmrl.lsposed.LsposedDeviceAcceptanceTest',
          f'{TEST_PKG}/{RUNNER}'], capture_output=True, text=True, timeout=180)
    log = result.stdout + '\n' + result.stderr
    (output/'instrumentation.log').write_text(log)
    try:
        raw = adb(a.device_serial, 'exec-out', 'run-as', APP_PKG,
                  'cat', 'files/mmrl_lsposed_device_acceptance.json')
        observed = json.loads(raw)
    except Exception as error:
        (output/'error.txt').write_text(f'Instrumented app evidence unavailable: {error}\n')
        raise RuntimeError('Instrumented evidence unavailable; see log in '+str(output)) from error
    (output/'device-acceptance.json').write_text(json.dumps(observed, indent=2, sort_keys=True)+'\n')
    if observed.get('schema') != 'mmrl.lsposed.device-acceptance.v1' or observed.get('target_package') != APP_PKG:
        raise RuntimeError('Unrecognized device evidence schema/package')
    status = observed.get('status', 'INVALID')
    ui = observed.get('ui_navigation', 'INCONCLUSIVE')
    result_data = {
        'schema': 'mmrl.lsposed.device-host-result.v1',
        'source_commit': receipt['source_commit'],
        'run_id': a.run_id, 'device_serial': a.device_serial,
        'device_properties': props, 'status': status, 'ui_navigation': ui,
        'qualification': ('PASS_RUNTIME_AND_UI' if status == 'PASS_VECTOR_DAEMON_AND_MODULE_QUERY' and ui == 'PASS'
                          else 'PASS_RUNTIME_UI_INCONCLUSIVE' if status == 'PASS_VECTOR_DAEMON_AND_MODULE_QUERY'
                          else 'NOT_QUALIFIED'),
        'instrumentation_completed': result.returncode == 0 and 'OK (1 test)' in log,
        'injection_verified': False, 'hooks_verified': False,
    }
    (output/'host-result.json').write_text(json.dumps(result_data, indent=2, sort_keys=True)+'\n')
    print(json.dumps(result_data, indent=2, sort_keys=True))
    print('Evidence saved: '+str(output))
    if not result_data['instrumentation_completed']:
        raise RuntimeError('Instrumentation reported a test failure')
    if result_data['qualification'] != 'PASS_RUNTIME_AND_UI':
        raise RuntimeError('Device acceptance INCONCLUSIVE/PARTIAL; inspect saved evidence, not a full pass')
    print('PASS: Vector daemon/module configuration and discovery screen observed on physical device')

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError, KeyError) as error:
        print('MMRL_DEVICE_ACCEPTANCE_REFUSED: '+str(error), file=sys.stderr)
        sys.exit(2)
