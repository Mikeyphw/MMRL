#!/usr/bin/env python3
"""Offline gates: no Gradle, Android device or network dependency."""
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET

import mmrl_lsposed_lint_gate as gate


class LintGateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'app/src/main').mkdir(parents=True)
        self.first = ('NewApi', 'Error', 'Requires API 27', 'app/src/main/themes.xml')
        self.second = ('UnusedResources', 'Warning', 'Unused value', 'app/src/main/themes.xml')
        self.render([self.first, self.second])
        self.original_outcome = os.environ.get('MMRL_LINT_DAG_OUTCOME')
        self.original_sha = os.environ.get('GITHUB_SHA')
        os.environ['MMRL_LINT_DAG_OUTCOME'] = 'success'
        os.environ['GITHUB_SHA'] = 'a'*40
        self.addCleanup(self.restore_env)

    def restore_env(self):
        for name, value in (('MMRL_LINT_DAG_OUTCOME', self.original_outcome), ('GITHUB_SHA', self.original_sha)):
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value

    def render(self, data):
        elem = ET.Element('issues', format='6')
        for id, severity, message, path in data:
            issue = ET.SubElement(elem, 'issue', id=id, severity=severity, message=message)
            ET.SubElement(issue, 'location', file=str(self.root / path), line='7')
        ET.ElementTree(elem).write(self.root/'report.xml', encoding='utf-8')

    def args(self, suffix):
        return SimpleNamespace(root=str(self.root), report='report.xml',baseline='ci/baseline.json',
                               candidate=str(self.root / f'candidate-{suffix}.json'),
                               evidence=str(self.root / f'evidence-{suffix}.json'))

    def bootstrap(self):
        self.assertEqual(gate.gate(self.args('new')), 2)
        gate.approve(SimpleNamespace(candidate=str(self.root/'candidate-new.json'),
                                     output=str(self.root/'ci/baseline.json'),
                                     reviewer='test', review_note='Reviewed the existing upstream findings'))

    def test_fail_closed_until_reviewed(self):
        self.assertEqual(gate.gate(self.args('no-baseline')), 2)
        self.assertTrue((self.root/'candidate-no-baseline.json').is_file())

    def test_existing_approved_issues_pass(self):
        self.bootstrap()
        self.assertEqual(gate.gate(self.args('approved')), 0)

    def test_extra_occurrence_fails(self):
        self.bootstrap()
        self.render([self.first, self.second, self.first])
        self.assertEqual(gate.gate(self.args('count')), 2)

    def test_new_warning_fails(self):
        self.bootstrap()
        self.render([self.first, self.second, ('NewIssue', 'Warning', 'Fresh problem', 'app/src/main/themes.xml')])
        self.assertEqual(gate.gate(self.args('new-warning')), 2)

    def test_resolved_findings_pass(self):
        self.bootstrap()
        self.render([self.first])
        self.assertEqual(gate.gate(self.args('fixed')), 0)

    def test_lint_node_failure_fails(self):
        self.bootstrap()
        os.environ['MMRL_LINT_DAG_OUTCOME'] = 'failure'
        self.assertEqual(gate.gate(self.args('failed-lint')), 2)

    def test_missing_report_fails(self):
        self.bootstrap()
        (self.root/'report.xml').unlink()
        self.assertEqual(gate.gate(self.args('missing')), 2)

    def test_internal_lint_failure_fails(self):
        self.bootstrap()
        self.render([self.first, ('LintError','Error','Broken analyzer','app/src/main/themes.xml')])
        self.assertEqual(gate.gate(self.args('lint-error')), 2)

    def test_tampering_with_baseline_fails(self):
        self.bootstrap()
        p=self.root/'ci/baseline.json'
        baseline=json.loads(p.read_text());baseline['total'] = 999
        p.write_text(json.dumps(baseline))
        self.assertEqual(gate.gate(self.args('tamper')), 2)


if __name__ == '__main__':
    unittest.main(verbosity=2)
