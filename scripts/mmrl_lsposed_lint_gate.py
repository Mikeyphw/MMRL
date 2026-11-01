#!/usr/bin/env python3
"""Fail-closed, source-controlled Android Lint baseline gate.

CI always reports diagnostics, including on baseline rejection. A candidate
cannot become an approved baseline without an explicit review command and
source-control commit. No tool here invokes Gradle.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

SCHEMA = 'mmrl.lsposed.lint-baseline.v1'
CANDIDATE_SCHEMA = 'mmrl.lsposed.lint-candidate.v1'
EVIDENCE_SCHEMA = 'mmrl.lsposed.lint-gate.v1'
SUPPORTED = {'Fatal', 'Error', 'Warning', 'Information', 'Hint', 'Ignore'}
FATAL_ISSUE_IDS = {'LintError', 'LintWarning'}


def persist(path: Path, value: dict) -> None:
    if path.exists() or path.is_symlink():
        raise ValueError(f'Refusing to overwrite existing evidence: {path}')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def digest(rows: list[dict]) -> str:
    data = json.dumps(rows, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()
    return hashlib.sha256(data).hexdigest()


def normalized_path(root: Path, raw: str) -> str:
    if not raw:
        raise ValueError('Lint location is missing its file path')
    raw = raw.removeprefix('file://')
    path = Path(raw)
    if not path.is_absolute():
        path = root / path
    try:
        relative = path.resolve(strict=False).relative_to(root)
    except ValueError as exc:
        raise ValueError('Lint report references path outside the repository: ' + raw) from exc
    return relative.as_posix()


def read_report(root: Path, report: Path) -> tuple[list[dict], int]:
    if report.is_symlink() or not report.is_file() or report.stat().st_size > 30_000_000:
        raise ValueError('Missing, unsafe, or oversized Android Lint XML report')
    try:
        tree = ET.parse(report)
    except ET.ParseError as exc:
        raise ValueError('Malformed Android Lint XML report') from exc
    if tree.getroot().tag != 'issues':
        raise ValueError('Unexpected Lint XML root element')
    items = Counter()
    count = 0
    for issue in tree.getroot().findall('issue'):
        issue_id = issue.get('id', '')
        severity = issue.get('severity', '')
        message = ' '.join((issue.get('message') or '').split())
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.-]{0,100}', issue_id):
            raise ValueError('Lint issue has invalid ID')
        if issue_id in FATAL_ISSUE_IDS:
            raise ValueError('Lint analyzer reported an internal failure: ' + issue_id)
        if severity not in SUPPORTED or not message:
            raise ValueError('Lint XML issue has missing severity or message')
        locations = issue.findall('location')
        if not locations:
            raise ValueError('Lint issue is missing location')
        # Primary location identifies an issue; other locations are context.
        path = normalized_path(root, locations[0].get('file', ''))
        key = (issue_id, severity, path, message)
        items[key] += 1
        count += 1
    entries = [dict(id=k[0], severity=k[1], path=k[2], message=k[3], count=v)
               for k, v in sorted(items.items())]
    return entries, count


def valid_entries(rows: object) -> list[dict]:
    if not isinstance(rows, list) or len(rows) > 10000:
        raise ValueError('Baseline issue list invalid')
    for item in rows:
        if not isinstance(item, dict) or set(item) != {'id', 'severity', 'path', 'message', 'count'}:
            raise ValueError('Baseline contains malformed issue entry')
        if not isinstance(item['count'], int) or isinstance(item['count'], bool) or item['count'] < 1:
            raise ValueError('Baseline count invalid')
        if not all(isinstance(item[k], str) and item[k] for k in ('id', 'severity', 'path', 'message')):
            raise ValueError('Baseline text field invalid')
        if item['severity'] not in SUPPORTED or item['id'] in FATAL_ISSUE_IDS:
            raise ValueError('Baseline contains unsupported issue')
        if item['path'].startswith('/') or '..' in Path(item['path']).parts:
            raise ValueError('Baseline path escapes repository')
    return rows


def read_baseline(path: Path) -> dict:
    if path.is_symlink() or not path.is_file():
        raise ValueError('Reviewed lint baseline absent (candidate has been uploaded for review)')
    obj = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(obj, dict) or obj.get('schema') != SCHEMA:
        raise ValueError('Invalid lint baseline schema')
    rows = valid_entries(obj.get('issues'))
    if rows != sorted(rows, key=lambda x:(x['id'], x['severity'], x['path'], x['message'])):
        raise ValueError('Baseline ordering not canonical')
    if not isinstance(obj.get('reviewed_by'), str) or len(obj['reviewed_by'].strip()) < 2:
        raise ValueError('Baseline reviewer absent')
    if not isinstance(obj.get('review_note'), str) or len(obj['review_note'].strip()) < 16:
        raise ValueError('Baseline rationale absent')
    if not re.fullmatch(r'[0-9a-f]{40}', obj.get('source_sha', '')):
        raise ValueError('Baseline source identity absent')
    if obj.get('digest') != digest(rows):
        raise ValueError('Baseline digest mismatch')
    if obj.get('total') != sum(x['count'] for x in rows):
        raise ValueError('Baseline count mismatch')
    return obj


def gate(args: argparse.Namespace) -> int:
    root = Path(args.root).resolve()
    report = root / args.report
    candidate_path = Path(args.candidate)
    evidence_path = Path(args.evidence)
    state = {'schema': EVIDENCE_SCHEMA, 'status': 'FAIL', 'report': args.report,
             'baseline': args.baseline, 'lint_node_outcome': os.environ.get('MMRL_LINT_DAG_OUTCOME', 'unknown')}
    try:
        entries, total = read_report(root, report)
        candidate = {'schema': CANDIDATE_SCHEMA, 'source_sha': os.environ.get('GITHUB_SHA', '0' * 40),
                     'total': total, 'digest': digest(entries), 'issues': entries}
        persist(candidate_path, candidate)
        state.update({'observed_total': total, 'observed_digest': candidate['digest']})
        if os.environ.get('MMRL_LINT_DAG_OUTCOME') != 'success':
            raise ValueError('Devtool lint node failed or was not executed')
        baseline = read_baseline(root / args.baseline)
        old = {(x['id'], x['severity'], x['path'], x['message']): x['count'] for x in baseline['issues']}
        regressions = []
        resolved = []
        for item in entries:
            key = (item['id'], item['severity'], item['path'], item['message'])
            diff = item['count'] - old.get(key, 0)
            if diff > 0:
                regressions.append({**item, 'new_occurrences': diff})
        observed = {(x['id'], x['severity'], x['path'], x['message']): x['count'] for x in entries}
        for old_item in baseline['issues']:
            key = (old_item['id'], old_item['severity'], old_item['path'], old_item['message'])
            diff = old_item['count'] - observed.get(key, 0)
            if diff > 0:
                resolved.append({**old_item, 'resolved_occurrences': diff})
        state['baseline_digest'] = baseline['digest']
        state['new_occurrences'] = sum(x['new_occurrences'] for x in regressions)
        state['resolved_occurrences'] = sum(x['resolved_occurrences'] for x in resolved)
        state['new_findings'] = regressions[:200]
        if regressions:
            raise ValueError(f'Lint baseline regression: {state["new_occurrences"]} new finding(s)')
        state['status'] = 'PASS'
    except (ValueError, OSError, ET.ParseError, json.JSONDecodeError) as exc:
        state['reason'] = str(exc)
    persist(evidence_path, state)
    print(json.dumps({k:v for k,v in state.items() if k != 'new_findings'}, sort_keys=True))
    if state['status'] != 'PASS':
        print('MMRL_LINT_GATE_REFUSED: '+state['reason'], file=sys.stderr)
        return 2
    return 0


def approve(args: argparse.Namespace) -> int:
    source = Path(args.candidate)
    if source.is_symlink() or not source.is_file():
        raise ValueError('Candidate not found')
    obj = json.loads(source.read_text(encoding='utf-8'))
    if obj.get('schema') != CANDIDATE_SCHEMA or not re.fullmatch(r'[0-9a-f]{40}', obj.get('source_sha', '')):
        raise ValueError('Candidate identity invalid')
    entries = valid_entries(obj.get('issues'))
    if digest(entries) != obj.get('digest') or sum(x['count'] for x in entries) != obj.get('total'):
        raise ValueError('Candidate fingerprint/count mismatch')
    if len(args.review_note.strip()) < 16 or len(args.reviewer.strip()) < 2:
        raise ValueError('Explicit reviewer and detailed review note required')
    baseline = {'schema': SCHEMA, 'source_sha': obj['source_sha'], 'issues': entries,
                'digest': obj['digest'], 'total': obj['total'],
                'reviewed_by': args.reviewer.strip(), 'review_note': args.review_note.strip()}
    persist(Path(args.output), baseline)
    print(f'Created reviewed baseline with {baseline["total"]} findings; commit it after inspection.')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    check = sub.add_parser('gate')
    for name in ('root','report','baseline','candidate','evidence'):
        check.add_argument('--'+name, required=True)
    confirm = sub.add_parser('approve')
    for name in ('candidate','output','reviewer','review-note'):
        confirm.add_argument('--'+name, required=True)
    args = parser.parse_args()
    try:
        return gate(args) if args.command == 'gate' else approve(args)
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print('MMRL_LINT_GATE_REFUSED: '+str(exc), file=sys.stderr)
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
