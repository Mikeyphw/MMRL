#!/usr/bin/env python3
"""Explicit opt-in physical-device Xposed hook lab with bounded scope and recovery journal.

Only changes: install/update the two lab APKs, optionally register THIS target
in THIS module scope, and temporarily enable THIS module. No other app is scoped.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time

REPO = 'Mikeyphw/MMRL'
TARGET = 'com.mikeyphw.mmrl.hooklab.target'
MODULE = 'com.mikeyphw.mmrl.hooklab.module'
URI = f'content://{TARGET}/probe'
ORIGINAL = 'ORIGINAL_PHASE05'
HOOKED = 'HOOKED_PHASE05'
RUN_BRANCHES = {'feature/lsposed-phase05-hook-acceptance', 'lsposed-vector'}


def call(*args, timeout=90):
    p = subprocess.run(args, text=True, capture_output=True, timeout=timeout)
    if p.returncode:
        raise RuntimeError(f'{args[0]} failed (exit={p.returncode}): {p.stderr[:400]} {p.stdout[:400]}')
    return p.stdout.strip()


def adb(serial, *args, timeout=90):
    return call('adb', '-s', serial, *args, timeout=timeout)


def prop(serial, name):
    return adb(serial, 'shell', 'getprop', name)


def verify_device(serial):
    if not re.fullmatch(r'[A-Za-z0-9._:-]{1,100}', serial):
        raise RuntimeError('Invalid explicit adb serial')
    if adb(serial, 'get-state') != 'device':
        raise RuntimeError('ADB device unavailable')
    fields = {k: prop(serial, k) for k in
              ('ro.kernel.qemu', 'ro.hardware', 'ro.build.characteristics',
               'ro.product.model', 'ro.build.version.sdk')}
    if (fields['ro.kernel.qemu'] == '1' or fields['ro.hardware'].lower() in ('goldfish', 'ranchu')
            or 'emulator' in fields['ro.build.characteristics'].lower()):
        raise RuntimeError('Real device required; emulator properties detected')
    # The same physical phone can be exposed as emulator-5554 by an ADB bridge;
    # use verified OS properties instead of assuming a transport naming scheme.
    if adb(serial, 'shell', 'am', 'get-current-user') != '0':
        raise RuntimeError('Only the primary Android user (0) is supported for the test')
    return fields


def cli(serial, *args):
    # This CLI path, root gateway, and subcommands are fixed. No user-controlled shell fragments.
    allowed = {
        ('status',), ('modules', 'ls'), ('modules', 'enable', MODULE),
        ('modules', 'disable', MODULE), ('scope', 'ls', MODULE),
        ('scope', 'add', MODULE, TARGET+'/0'),
        ('scope', 'rm', MODULE, TARGET+'/0'),
    }
    if tuple(args) not in allowed:
        raise RuntimeError('Command outside lab-only Vector allowlist')
    command = '/data/adb/modules/zygisk_vector/cli --json ' + ' '.join(args)
    result = adb(serial, 'shell', 'su', '-c', command, timeout=30)
    try:
        parsed = json.loads(result)
    except json.JSONDecodeError as exc:
        raise RuntimeError('Vector CLI did not return structured JSON') from exc
    if not isinstance(parsed, dict) or parsed.get('success') is not True:
        raise RuntimeError('Vector CLI rejected read or change: '+str(parsed.get('error')))
    return parsed.get('data')


def modules(serial):
    rows = cli(serial, 'modules', 'ls')
    if not isinstance(rows, list) or not all(isinstance(x, dict) for x in rows):
        raise RuntimeError('Unexpected Vector module inventory')
    choices = [r for r in rows if r.get('PACKAGE') == MODULE]
    if len(choices) != 1 or choices[0].get('STATUS') not in ('enabled', 'disabled'):
        raise RuntimeError('Disposable lab module missing or ambiguous in Vector inventory')
    return choices[0]['STATUS']


def scope(serial):
    rows = cli(serial, 'scope', 'ls', MODULE)
    if not isinstance(rows, list):
        raise RuntimeError('Unexpected Vector scope format')
    out = []
    for item in rows:
        if not isinstance(item, dict) or not isinstance(item.get('APP_PACKAGE'), str):
            raise RuntimeError('Malformed Vector scope record')
        user = item.get('USER_ID')
        if isinstance(user, bool) or not isinstance(user, int):
            raise RuntimeError('Unsupported user identifier in scope')
        out.append(f"{item['APP_PACKAGE']}/{user}")
    if len(set(out)) != len(out):
        raise RuntimeError('Duplicate Vector scope records')
    return sorted(out)


def probe(serial):
    output = adb(serial, 'shell', 'content', 'query', '--uri', URI, timeout=35)
    matches = re.findall(r'(?m)^Row:\s*0\s+value=([A-Z0-9_]+)\s*$', output)
    if len(matches) != 1:
        raise RuntimeError('Lab probe produced no unambiguous row: '+output[:300])
    return matches[0]


def restart_target(serial):
    adb(serial, 'shell', 'am', 'force-stop', TARGET)
    # Provider query starts an isolated test target process; no other apps are touched.
    time.sleep(1)
    return probe(serial)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for part in iter(lambda: f.read(1048576), b''):
            h.update(part)
    return h.hexdigest()


def persist(path, doc):
    tmp = path.with_suffix('.tmp')
    if tmp.exists():
        # Journal is the committed authority; a prior interrupted staging file is not.
        if path.exists():
            tmp.unlink()
        else:
            raise RuntimeError('Uncommitted initial journal; inspect before retry')
    tmp.write_text(json.dumps(doc, indent=2, sort_keys=True)+'\n')
    tmp.replace(path)


def set_phase(path, doc, phase):
    doc['phase'] = phase
    persist(path, doc)


def restore(serial, path, journal):
    # Recovery only applies to an originally DISABLED test module, never user-enabled modules.
    if (journal.get('schema') != 'mmrl.lsposed.hook-lab-journal.v1'
            or journal.get('serial') != serial
            or journal.get('original_module_state') != 'disabled'
            or journal.get('original_scope') not in ([], [TARGET+'/0'])):
        raise RuntimeError('Refusing ambiguous or non-lab recovery journal')
    set_phase(path, journal, 'RESTORING_MODULE')
    cli(serial, 'modules', 'disable', MODULE)
    if modules(serial) != 'disabled':
        raise RuntimeError('Vector did not confirm lab module disabled')
    if journal['original_scope'] == []:
        set_phase(path, journal, 'RESTORING_SCOPE')
        existing = scope(serial)
        if existing == [TARGET+'/0']:
            cli(serial, 'scope', 'rm', MODULE, TARGET+'/0')
        elif existing:
            raise RuntimeError('Scope changed unexpectedly; refuse removal of other entries')
    final_scope = scope(serial)
    if final_scope != journal['original_scope']:
        raise RuntimeError('Scope did not restore to recorded baseline')
    set_phase(path, journal, 'RESTORED')


def download_pair(run_id, out):
    run = json.loads(call('gh', 'api', f'repos/{REPO}/actions/runs/{run_id}'))
    if (run.get('status') != 'completed' or run.get('conclusion') != 'success'
            or run.get('head_branch') not in RUN_BRANCHES):
        raise RuntimeError('GitHub run is not an accepted successful lab run')
    attempt = int(run['run_attempt'])
    out.mkdir(parents=True, exist_ok=False)
    call('gh', 'run', 'download', str(run_id), '-R', REPO,
         '-n', f'mmrl-lsposed-hook-lab-{run_id}-attempt{attempt}', '-D', str(out))
    receipt = json.loads((out/'pair-receipt.json').read_text())
    target_apk, module_apk = out/'MMRL-HookLab-Target-debug.apk', out/'MMRL-HookLab-Module-debug.apk'
    expected = {
        'schema': 'mmrl.lsposed.hook-lab-pair.v1',
        'status': 'VERIFIED_DEBUG_PAIR_NOT_DEVICE_QUALIFIED',
        'run_id': run_id, 'attempt': attempt,
        'head_sha': run['head_sha'],
        'target_package': TARGET, 'module_package': MODULE,
        'target_sha256': digest(target_apk), 'module_sha256': digest(module_apk),
        'static_scope': [TARGET], 'modern_api': 102,
        'device_qualified': False, 'injection_verified': False, 'hooks_verified': False,
    }
    if any(receipt.get(k) != v for k, v in expected.items()):
        raise RuntimeError('Artifact pair/run head mismatch; no device changes made')
    # PR checkout uses GitHub's synthetic merge SHA, not the PR head SHA.
    # The CI signer already asserted git HEAD == GITHUB_SHA; preserve both IDs.
    if not re.fullmatch(r'[0-9a-f]{40}', str(receipt.get('source_commit', ''))):
        raise RuntimeError('Missing exact CI checkout source identity')
    return target_apk, module_apk, receipt


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('run_id', type=int)
    p.add_argument('serial', help='Explicit ADB transport, e.g. localhost:5555')
    p.add_argument('--authorize-lab-changes', action='store_true',
                   help='Authorize reversible changes to the two lab packages only')
    p.add_argument('--recover', type=Path, help='Restore previous journal; no APK install')
    p.add_argument('--out', type=Path, default=Path.home()/'.local/state/mmrl-phase05')
    a = p.parse_args()
    props = verify_device(a.serial)
    if not a.authorize_lab_changes:
        raise RuntimeError('Add --authorize-lab-changes after reviewing the lab-only mutations')
    if a.recover:
        journal = json.loads(a.recover.read_text())
        if journal.get('run_id') != a.run_id:
            raise RuntimeError('Run ID does not match recovery journal')
        restore(a.serial, a.recover, journal)
        print('RESTORED: disabled only the lab module and restored its original target scope')
        return
    artifact_path = a.out / f'run-{a.run_id}' / a.serial / 'pair'
    target, module, receipt = download_pair(a.run_id, artifact_path)
    # Never change Vector configuration if daemon API is not compatible with lab.
    status = cli(a.serial, 'status')
    if not isinstance(status, dict) or int(status.get('API Version', 0)) < 102:
        raise RuntimeError('Vector modern API 102+ required; no APK installed')
    adb(a.serial, 'install', '-r', str(target))
    adb(a.serial, 'install', '-r', str(module))
    previous = modules(a.serial)
    original_scope = scope(a.serial)
    if previous != 'disabled':
        raise RuntimeError('Lab module was already enabled; refuse to override preexisting state')
    if original_scope not in ([], [TARGET+'/0']):
        raise RuntimeError('Refusing lab with unexpected preexisting scope: '+str(original_scope))
    journal_path = artifact_path.parent/'journal.json'
    journal = {
        'schema':'mmrl.lsposed.hook-lab-journal.v1', 'serial':a.serial,
        'run_id':a.run_id, 'source_commit':receipt['source_commit'],
        'original_module_state':previous, 'original_scope':original_scope,
        'device_properties':props, 'phase':'PRE_MUTATION'
    }
    persist(journal_path, journal)
    observed = {'schema':'mmrl.lsposed.hook-execution.v1','run_id':a.run_id,
                'source_commit':receipt['source_commit'], 'device':props,
                'target_package':TARGET,'module_package':MODULE,
                'baseline':None,'hooked':None,'restored':None,
                'injection_verified':False,'hooks_verified':False}
    error = None
    try:
        # Observe genuine unhooked behavior before any module enable operation.
        baseline = restart_target(a.serial)
        observed['baseline'] = baseline
        if baseline != ORIGINAL:
            raise RuntimeError('Baseline already modified; refuse activation')
        if original_scope == []:
            set_phase(journal_path, journal, 'INTEND_SCOPE_ADD')
            cli(a.serial, 'scope', 'add', MODULE, TARGET+'/0')
        if scope(a.serial) != [TARGET+'/0']:
            raise RuntimeError('Scope is not exactly the disposable lab target')
        set_phase(journal_path, journal, 'INTEND_ENABLE')
        cli(a.serial, 'modules', 'enable', MODULE)
        if modules(a.serial) != 'enabled':
            raise RuntimeError('Vector did not confirm test module enabled')
        set_phase(journal_path, journal, 'MODULE_ENABLED')
        for _ in range(4):
            observed['hooked'] = restart_target(a.serial)
            if observed['hooked'] == HOOKED:
                break
            time.sleep(2)
        if observed['hooked'] != HOOKED:
            raise RuntimeError('Hook did not change the target return value')
    except Exception as exc:
        error = str(exc)
    finally:
        try:
            restore(a.serial, journal_path, journal)
            observed['restored'] = restart_target(a.serial)
        except Exception as recovery_exc:
            observed['restore_error'] = str(recovery_exc)
            observed['qualification'] = 'RECOVERY_REQUIRED'
            error = (error or '') + ' | RECOVERY_REQUIRED: '+str(recovery_exc)
        if observed['restored'] == ORIGINAL and journal.get('phase') == 'RESTORED':
            observed['injection_verified'] = observed['hooked'] == HOOKED
            observed['hooks_verified'] = observed['hooked'] == HOOKED
            observed['qualification'] = ('PASS_HOOK_AND_RESTORATION' if
                observed['baseline'] == ORIGINAL and observed['hooked'] == HOOKED
                else 'NOT_QUALIFIED')
        if error:
            observed['error'] = error
        evidence_path = artifact_path.parent/'hook-execution.json'
        persist(evidence_path, observed)
        print(json.dumps(observed, indent=2, sort_keys=True))
        print('Evidence: '+str(evidence_path))
        print('Recovery journal: '+str(journal_path))
    if error or observed.get('qualification') != 'PASS_HOOK_AND_RESTORATION':
        raise RuntimeError(error or 'Hook execution not proven; inspect JSON evidence')
    print('PASS: genuine scoped Vector hook fired, and original behavior was restored')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, OSError, KeyError, subprocess.SubprocessError) as exc:
        print('MMRL_PHASE05_REFUSED: '+str(exc), file=sys.stderr)
        sys.exit(2)
