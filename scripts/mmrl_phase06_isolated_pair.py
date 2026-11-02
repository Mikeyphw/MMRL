#!/usr/bin/env python3
"""CI-only signer, manifest and source attestation for a run-unique Phase06 APK pair."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

RUNNER = 'androidx.test.runner.AndroidJUnitRunner'


def command(*parts):
    return subprocess.check_output(parts, text=True, stderr=subprocess.STDOUT, timeout=90)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda: f.read(1048576), b''):
            h.update(block)
    return h.hexdigest()


def signer(tool, apk):
    output = command(str(tool), 'verify', '--print-certs', str(apk))
    fingerprints = re.findall(r'(?im)^Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$', output)
    if len(fingerprints) != 1:
        raise RuntimeError('Expected a single verified APK signer')
    return fingerprints[0]


def apk_package(tool, apk):
    badging = command(str(tool), 'dump', 'badging', str(apk))
    match = re.search(r"(?m)^package: name='([^']+)'", badging)
    if not match:
        raise RuntimeError('Missing APK package name')
    return match.group(1)


def main():
    cli = argparse.ArgumentParser()
    cli.add_argument('--main-apk', type=Path, required=True)
    cli.add_argument('--tests-directory', type=Path, required=True)
    cli.add_argument('--out', type=Path, required=True)
    args = cli.parse_args()
    if os.getenv('GITHUB_ACTIONS') != 'true' or os.getenv('GITHUB_REPOSITORY') != 'Mikeyphw/MMRL':
        raise RuntimeError('Only source-bound GitHub Actions may attest the APK pair')
    run_id, attempt = int(os.environ['GITHUB_RUN_ID']), int(os.environ['GITHUB_RUN_ATTEMPT'])
    app_id = f'com.dergoogler.mmrl.phase06.r{run_id}a{attempt}'
    test_id = app_id + '.test'
    test_apks = list(args.tests_directory.rglob('*.apk'))
    if len(test_apks) != 1 or not args.main_apk.is_file():
        raise RuntimeError('Exactly one main APK and one instrumentation APK required')
    test_apk = test_apks[0]
    tools = Path(os.environ['ANDROID_HOME'])/'build-tools'/'36.0.0'
    aapt, apksigner = tools/'aapt', tools/'apksigner'
    if apk_package(aapt, args.main_apk) != app_id or apk_package(aapt, test_apk) != test_id:
        raise RuntimeError('APK identities are not unique to this GitHub run and attempt')
    cert_main, cert_test = signer(apksigner, args.main_apk), signer(apksigner, test_apk)
    if cert_main != cert_test:
        raise RuntimeError('App and instrumentation signing certificates differ')
    test_manifest = command(str(aapt), 'dump', 'xmltree', str(test_apk), 'AndroidManifest.xml')
    if not all(item in test_manifest for item in ('android:targetPackage', app_id,
                                                  'android:name', RUNNER)):
        raise RuntimeError('Instrumentation target or runner mismatch')
    main_manifest = command(str(aapt), 'dump', 'xmltree', str(args.main_apk), 'AndroidManifest.xml')
    if 'com.dergoogler.mmrl.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' in main_manifest:
        raise RuntimeError('Isolated test claims the original MMRL custom permission')
    if f'{app_id}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' not in main_manifest:
        raise RuntimeError('Unique custom permission missing from isolated APK')
    source_commit = command('git', 'rev-parse', 'HEAD').strip()
    if source_commit != os.environ['GITHUB_SHA']:
        raise RuntimeError('CI source checkout mismatch')
    head_sha = os.environ['MMRL_PR_HEAD_SHA']
    if not re.fullmatch('[0-9a-f]{40}', head_sha):
        raise RuntimeError('Missing PR head SHA')
    args.out.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(args.main_apk, args.out/'MMRL-Phase06-Isolated-App.apk')
    shutil.copyfile(test_apk, args.out/'MMRL-Phase06-Isolated-Test.apk')
    receipt = {
        'schema': 'mmrl.lsposed.phase06.isolated-apk-pair.v1',
        'status': 'VERIFIED_ISOLATED_PAIR_NOT_DEVICE_QUALIFIED',
        'run_id': run_id, 'attempt': attempt, 'source_commit': source_commit,
        'head_sha': head_sha, 'main_package': app_id, 'test_package': test_id,
        'runner': RUNNER, 'main_sha256': digest(args.main_apk),
        'test_sha256': digest(test_apk), 'signer_sha256': cert_main,
        'device_qualified': False,
    }
    (args.out/'pair-receipt.json').write_text(json.dumps(receipt, indent=2, sort_keys=True) + '\n')
    print(json.dumps(receipt, sort_keys=True))


if __name__ == '__main__':
    main()
