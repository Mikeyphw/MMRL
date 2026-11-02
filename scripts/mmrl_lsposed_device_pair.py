#!/usr/bin/env python3
"""CI-only build-pair verification; NEVER asserts on-device qualification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

MAIN_PKG = 'com.dergoogler.mmrl.debug'
TEST_PKG = 'com.dergoogler.mmrl.debug.test'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'

def run(*args):
    return subprocess.check_output(args, text=True, timeout=90, stderr=subprocess.STDOUT)

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def signer(apksigner, path):
    out = run(str(apksigner), 'verify', '--print-certs', str(path))
    matches = re.findall(r'(?im)^Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$', out)
    if len(matches) != 1:
        raise ValueError('Expected exactly one verified signer')
    return matches[0]

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--main-apk', type=Path, required=True)
    p.add_argument('--tests-directory', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    if os.getenv('GITHUB_ACTIONS') != 'true' or os.getenv('GITHUB_REPOSITORY') != 'Mikeyphw/MMRL':
        raise SystemExit('CI source authority required')
    test_candidates = list(a.tests_directory.rglob('*.apk'))
    if len(test_candidates) != 1 or not a.main_apk.is_file():
        raise SystemExit('Expected exactly one test APK and one Devtool-verified main APK')
    test = test_candidates[0]
    build_tools = Path(os.environ['ANDROID_HOME'])/'build-tools'/'36.0.0'
    apksigner = build_tools/'apksigner'
    aapt = build_tools/'aapt'
    sig_main, sig_test = signer(apksigner, a.main_apk), signer(apksigner, test)
    if sig_main != sig_test:
        raise SystemExit('Refusing APK pair with differing signing certificates')
    main_badging = run(str(aapt), 'dump', 'badging', str(a.main_apk))
    test_badging = run(str(aapt), 'dump', 'badging', str(test))
    if not re.search(r"(?m)^package: name='" + re.escape(MAIN_PKG) + r"'\s", main_badging):
        raise SystemExit('Unexpected main APK package')
    if not re.search(r"(?m)^package: name='" + re.escape(TEST_PKG) + r"'\s", test_badging):
        raise SystemExit('Unexpected test APK package')
    manifest = run(str(aapt), 'dump', 'xmltree', str(test), 'AndroidManifest.xml')
    if (f'android:targetPackage' not in manifest or MAIN_PKG not in manifest or
            'android:name' not in manifest or RUNNER not in manifest):
        raise SystemExit('Instrumentation manifest lacks verified target or runner')
    out = a.out
    out.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(test, out/'MMRL-LSPosed-Device-OfficialDebug-androidTest.apk')
    receipt = {
        'schema': 'mmrl.lsposed.device-apk-pair.v1',
        'status': 'VERIFIED_DEBUG_PAIR_NOT_DEVICE_QUALIFIED',
        'run_id': int(os.environ['GITHUB_RUN_ID']),
        'attempt': int(os.environ['GITHUB_RUN_ATTEMPT']),
        'source_commit': run('git', 'rev-parse', 'HEAD').strip(),
        'main_package': MAIN_PKG,
        'test_package': TEST_PKG,
        'runner': RUNNER,
        'main_sha256': sha(a.main_apk),
        'test_sha256': sha(test),
        'signer_sha256': sig_main,
        'device_qualified': False,
        'device_tests_executed': False,
    }
    (out/'pair-receipt.json').write_text(json.dumps(receipt, indent=2, sort_keys=True)+'\n')
    print(json.dumps(receipt, sort_keys=True))

if __name__ == '__main__':
    main()
