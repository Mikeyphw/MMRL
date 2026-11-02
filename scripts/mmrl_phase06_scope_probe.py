#!/usr/bin/env python3
"""Read-only Vector scope audit via isolated app UID; no module or scope mutations."""
from __future__ import annotations
import argparse
import json
import re
import subprocess
import sys

APP_RX = re.compile(r'com\.dergoogler\.mmrl\.phase06\.r[0-9]+a[0-9]+\Z')
MODULE_RX = re.compile(r'[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+\Z')
CLI = '/data/adb/modules/zygisk_vector/cli'


def read(serial: str, app: str, command: str, module: str | None = None):
    if not APP_RX.fullmatch(app):
        raise ValueError('Expected the exact Phase06 isolated app package family')
    if command not in ('status', 'modules ls', 'scope ls'):
        raise ValueError('Read-only command not allowlisted')
    if command == 'scope ls' and (not module or not MODULE_RX.fullmatch(module)):
        raise ValueError('Invalid scope module identifier')
    if module and command != 'scope ls':
        raise ValueError('Unexpected module parameter')
    op = command + (f' {module}' if module else '')
    shell = f'run-as {app} sh -c \'su -c "{CLI} --json {op}"\''
    proc = subprocess.run(['adb', '-s', serial, 'shell', shell], capture_output=True,
                          text=True, timeout=25)
    if proc.returncode != 0:
        raise RuntimeError(f'{command} failed (adb exit={proc.returncode}): {proc.stderr[:120]}')
    data = json.loads(proc.stdout)
    if data.get('success') is not True:
        raise RuntimeError(f'{command} rejected: {str(data.get("error"))[:120]}')
    return data['data']


def scope_issues(rows: object) -> tuple[int, list[str]]:
    if not isinstance(rows, list):
        raise ValueError('scope ls did not return an array')
    anomalies = []
    special = []
    for item in rows:
        if not isinstance(item, dict):
            anomalies.append('non-object scope record')
            continue
        pkg = item.get('APP_PACKAGE')
        uid = item.get('USER_ID')
        if pkg in ('android', 'system'):
            special.append(pkg + ' (Vector single-segment scope entry; read only)')
        elif not isinstance(pkg, str) or not MODULE_RX.fullmatch(pkg):
            anomalies.append('nonstandard scope target: '+repr(str(pkg)[:100]))
        if type(uid) is not int or not 0 <= uid < 2**31:
            anomalies.append(f'noninteger or out-of-range user ID type={type(uid).__name__}')
    return len(rows), anomalies, special


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='localhost:5555')
    parser.add_argument('--app', default='com.dergoogler.mmrl.phase06.r41430978979a1')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9._:-]{1,100}', args.serial):
        raise ValueError('Invalid ADB serial')
    status = read(args.serial, args.app, 'status')
    print('Vector API:', status.get('API Version'))
    rows = read(args.serial, args.app, 'modules ls')
    if not isinstance(rows, list) or len(rows) > 512:
        raise RuntimeError('Unrecognized or oversized module listing')
    print('Modules:', len(rows))
    errors = []
    total = 0
    anomaly_count = 0
    special_count = 0
    for item in rows:
        pkg = item.get('PACKAGE') if isinstance(item, dict) else None
        try:
            if not isinstance(pkg, str) or not MODULE_RX.fullmatch(pkg):
                raise ValueError('Invalid module package')
            scopes = read(args.serial, args.app, 'scope ls', pkg)
            count, anomalies, special = scope_issues(scopes)
            total += count
            anomaly_count += len(anomalies)
            special_count += len(special)
            for identifier in special:
                print('SCOPE_SPECIAL', pkg, identifier)
            for anomaly in anomalies:
                print('SCOPE_ANOMALY', pkg, anomaly)
        except (RuntimeError, ValueError, KeyError, subprocess.TimeoutExpired, json.JSONDecodeError) as exc:
            errors.append((str(pkg)[:150], str(exc)[:160]))
    for pkg, err in errors:
        print('SCOPE_ERROR', pkg, err)
    print(f'READ_ONLY_SCOPE_AUDIT: modules={len(rows)} scope_entries={total} special={special_count} anomalies={anomaly_count} errors={len(errors)}')
    print('No module/scope writes performed.')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired, json.JSONDecodeError) as ex:
        print('PHASE06_SCOPE_PROBE_REFUSED: '+str(ex)[:240], file=sys.stderr)
        sys.exit(2)
