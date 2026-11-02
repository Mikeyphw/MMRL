#!/usr/bin/env python3
"""CI-only verification of two disposable lab APKs; never grants device acceptance."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

TARGET = 'com.mikeyphw.mmrl.hooklab.target'
MODULE = 'com.mikeyphw.mmrl.hooklab.module'


def cmd(*args):
    return subprocess.check_output(args, text=True, timeout=75, stderr=subprocess.STDOUT)


def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1048576), b''):
            h.update(block)
    return h.hexdigest()


def verify(apk, package, signer, aapt):
    s = cmd(str(signer), 'verify', '--print-certs', str(apk))
    certs = re.findall(r'(?mi)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$', s)
    if len(certs) != 1:
        raise ValueError('One verified signer is mandatory: ' + package)
    info = cmd(str(aapt), 'dump', 'badging', str(apk))
    if not re.search(r"(?m)^package: name='" + re.escape(package) + r"'\s", info):
        raise ValueError('Unexpected APK identity: ' + package)
    return certs[0]


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--target', type=Path, required=True)
    p.add_argument('--module', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    if (os.getenv('GITHUB_ACTIONS') != 'true' or os.getenv('GITHUB_REPOSITORY') != 'Mikeyphw/MMRL'
            or cmd('git', 'rev-parse', 'HEAD').strip() != os.getenv('GITHUB_SHA')):
        raise RuntimeError('Expected an exact-source GitHub Actions build')
    bt = Path(os.environ['ANDROID_HOME'])/'build-tools'/'36.0.0'
    target_cert = verify(a.target, TARGET, bt/'apksigner', bt/'aapt')
    module_cert = verify(a.module, MODULE, bt/'apksigner', bt/'aapt')
    if target_cert != module_cert:
        raise ValueError('Lab APK signing identities differ')
    with zipfile.ZipFile(a.module) as z:
        expected = {
            'META-INF/xposed/java_init.list': 'com.mikeyphw.mmrl.hooklab.module.LabHookModule\n',
            'META-INF/xposed/module.prop': 'minApiVersion=102\ntargetApiVersion=102\nstaticScope=true\nexceptionMode=protective\n',
            'META-INF/xposed/scope.list': TARGET+'\n',
        }
        for path, value in expected.items():
            if z.read(path).decode() != value:
                raise ValueError('Incorrect modern Xposed metadata: '+path)
        if any('legacy' in k.lower() or 'xposed_init' in k for k in z.namelist()):
            raise ValueError('Unexpected legacy hook entry')
    head_sha = os.environ.get('MMRL_PR_HEAD_SHA', '')
    if not re.fullmatch(r'[0-9a-f]{40}', head_sha):
        raise ValueError('Unverified GitHub PR/push head identity')
    a.out.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(a.target, a.out/'MMRL-HookLab-Target-debug.apk')
    shutil.copyfile(a.module, a.out/'MMRL-HookLab-Module-debug.apk')
    receipt = {
        'schema': 'mmrl.lsposed.hook-lab-pair.v1',
        'status': 'VERIFIED_DEBUG_PAIR_NOT_DEVICE_QUALIFIED',
        'run_id': int(os.environ['GITHUB_RUN_ID']),
        'attempt': int(os.environ['GITHUB_RUN_ATTEMPT']),
        'source_commit': os.environ['GITHUB_SHA'],
        'head_sha': head_sha,
        'target_package': TARGET,
        'module_package': MODULE,
        'target_sha256': sha(a.target),
        'module_sha256': sha(a.module),
        'signer_sha256': target_cert,
        'static_scope': [TARGET],
        'modern_api': 102,
        'device_qualified': False,
        'injection_verified': False,
        'hooks_verified': False,
    }
    (a.out/'pair-receipt.json').write_text(json.dumps(receipt, sort_keys=True, indent=2)+'\n')
    print(json.dumps(receipt, sort_keys=True))

if __name__ == '__main__':
    main()
