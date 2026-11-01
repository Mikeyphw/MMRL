#!/usr/bin/env python3
"""GitHub-only, non-release R37 APK pair inspection and provenance receipt."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re

APP_PACKAGE = 'com.mikeyphw.mmrl.debug'
TEST_PACKAGE = APP_PACKAGE + '.test'
RUNNER = 'androidx.test.runner.AndroidJUnitRunner'
DEVTOOL_SHA = '246f168ebdef6d9b25c194268c810c877d099fa3'
HEX64 = re.compile(r'[a-f0-9]{64}\Z')
HEX40 = re.compile(r'[a-f0-9]{40}\Z')


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def run_tool(*args: str) -> str:
    # The canonical ACTIONS-05 tool resolver locates SDK build-tools on hosted
    # runners without assuming apksigner/aapt are exported on PATH.
    from devtool.actions_android import _tool, _run
    return _run([_tool(args[0]), *args[1:]], purpose='R37 Android APK verification')


def file_sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as inp:
        for block in iter(lambda: inp.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def apk_info(path: Path, expected_package: str) -> dict:
    require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size < 160 * 1024 * 1024, 'Missing/unsafe APK')
    signature = run_tool('apksigner', 'verify', '--verbose', '--print-certs', str(path))
    certs = re.findall(r"(?im)^Signer #\d+ certificate SHA-256 digest:\s*([a-f0-9]{64})\s*$", signature)
    require(len(certs) == 1 and HEX64.fullmatch(certs[0].lower()) is not None, 'APK signer is ambiguous or invalid')
    badging = run_tool('aapt', 'dump', 'badging', str(path))
    match = re.search(r"(?m)^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']*)'", badging)
    require(match is not None and match[1] == expected_package, 'Unexpected Android APK package')
    return {'package': match[1], 'version_code': match[2], 'version_name': match[3],
            'sha256': file_sha(path), 'size': path.stat().st_size,
            'signer_sha256': certs[0].lower(), 'signature_verified': True}


def verify_pair(app: dict, test: dict, manifest: str, plan: dict, checked: dict,
                receipt: dict, *, source: str, run_id: int, attempt: int) -> dict:
    require(HEX40.fullmatch(source) is not None and run_id > 0 and attempt > 0, 'Invalid GitHub execution identity')
    require(app['package'] == APP_PACKAGE and test['package'] == TEST_PACKAGE, 'Wrong app/test package pair')
    require(app['signer_sha256'] == test['signer_sha256'], 'App/test signers differ')
    require('E: instrumentation' in manifest, 'Test APK has no instrumentation declaration')
    require(re.search(r'android:targetPackage[^\n]*="' + re.escape(APP_PACKAGE) + '"', manifest) is not None, 'Test APK does not target debug application')
    require(re.search(r'android:name[^\n]*="' + re.escape(RUNNER) + '"', manifest) is not None, 'Unknown test runner')
    require(all(plan.get(k) == v for k,v in {'source_sha':source,'devtool_sha':DEVTOOL_SHA,
        'release_qualified':False,'device_qualified':False}.items()), 'R36 source/provider plan mismatch')
    require(all(checked.get(k) == v for k,v in {'source_sha':source,'run_id':run_id,
        'attempt':attempt,'apk_sha256':app['sha256'],'signer_sha256':app['signer_sha256'],
        'signature_independently_verified':True,'release_qualified':False,'device_qualified':False}.items()), 'R36 independent verification mismatch')
    require(all(receipt.get(k) == v for k,v in {'source_commit':source,'run_id':str(run_id),
        'attempt':attempt,'kind':'devtool.actions.node-handoff','schema_version':2,
        'authenticated':False,'release_qualified':False}.items()), 'Native R36 handoff mismatch')
    files=receipt.get('files')
    require(isinstance(files,list) and len(files)==1 and files[0].get('sha256')==app['sha256'] and
        files[0].get('size')==app['size'], 'R36 native handoff does not describe app bytes')
    require(plan.get('graph_sha256')==receipt.get('graph_sha256')==checked.get('graph_sha256'), 'R36 graph changed')
    return {'schema':'mmrl.r37.hosted-pair.v1','status':'HOSTED_PAIR_VERIFIED_NON_DEVICE',
        'repository':'Mikeyphw/MMRL','workflow':'mmrl-r37-paired-device-apks.yml',
        'source_sha':source,'run_id':run_id,'attempt':attempt,'devtool_sha':DEVTOOL_SHA,
        'graph_sha256':receipt['graph_sha256'], 'r36_native_workflow_sha256':receipt['workflow_sha256'],
        'app':app, 'test':test, 'instrumentation_runner':TEST_PACKAGE+'/'+RUNNER,
        'target_package':APP_PACKAGE,'app_test_compatible':True,
        'remote_actions_artifact_authenticated':False,'device_qualified':False,
        'consumer_qualified':False,'release_qualified':False,
        'test_execution':'NOT_RUN'}


def main() -> None:
    parser=argparse.ArgumentParser()
    for name in ('app','test','r36-plan','r36-verification','r36-native-receipt','output'):
        parser.add_argument('--'+name, required=True, type=Path)
    args=parser.parse_args()
    require(os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('RUNNER_OS')=='Linux' and
        os.environ.get('GITHUB_REPOSITORY')=='Mikeyphw/MMRL' and
        os.environ.get('GITHUB_EVENT_NAME')=='workflow_dispatch', 'Only GitHub hosted workflow_dispatch may attest this pair')
    source=os.environ.get('GITHUB_SHA','').lower()
    run_id=int(os.environ.get('GITHUB_RUN_ID','0')); attempt=int(os.environ.get('GITHUB_RUN_ATTEMPT','0'))
    app=apk_info(args.app,APP_PACKAGE)
    test=apk_info(args.test,TEST_PACKAGE)
    manifest=run_tool('aapt','dump','xmltree',str(args.test),'AndroidManifest.xml')
    inputs=[json.loads(p.read_text()) for p in (args.r36_plan,args.r36_verification,args.r36_native_receipt)]
    payload=verify_pair(app,test,manifest,*inputs,source=source,run_id=run_id,attempt=attempt)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('x',encoding='utf-8') as out:
        json.dump(payload,out,indent=2,sort_keys=True);out.write('\n')
    print(json.dumps({'status':payload['status'],'app_sha256':app['sha256'],'test_sha256':test['sha256']},sort_keys=True))

if __name__=='__main__':
    main()
