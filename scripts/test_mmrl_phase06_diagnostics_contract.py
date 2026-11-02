#!/usr/bin/env python3
"""Offline contract regression for Phase06 root read-only diagnostics."""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]

class Phase06DiagnosticsContract(unittest.TestCase):
    def test_read_only_system_package_and_safe_mutation_guard(self):
        text=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/VectorManager.kt').read_text()
        self.assertIn('validScopePackage(pkg)',text)
        self.assertIn('pkg == "android"',text)
        self.assertIn('pkg == "system"',text)
        self.assertIn('private fun safeTarget',text)
        self.assertIn('require(valid(target))',text)
        self.assertIn('if (!validScopePackage(pkg) || user !is Number',text)
        self.assertNotIn('"scope", "set"', text)

    def test_instrumentation_evidence_fail_closed(self):
        text=(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        for key in ('"stage"', '"error_detail"', '"scope:"', '"initial_inventory"',
                    '"inventory_recheck"','"PASS_READ_ONLY_INVENTORY"',
                    'report.optString("status") == "PASS_READ_ONLY_INVENTORY"'):
            self.assertIn(key,text)
        self.assertIn('pending.renameTo(committed)', text)
        self.assertNotIn('VectorManager.change(',text)

    def test_scope_probe_never_writes(self):
        script=(ROOT/'scripts/mmrl_phase06_scope_probe.py').read_text()
        for token in ('run-as', 'modules ls', 'scope ls', 'scope_issues', 'read_only'):
            self.assertIn(token.lower(),script.lower())
        for forbidden in ('"scope add"', '"scope rm"', '"modules enable"', '"modules disable"',
                          '"pm clear"', 'uninstall(', '"reboot"'):
            self.assertNotIn(forbidden,script)

    def test_ci_runs_new_contract(self):
        text=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        self.assertIn('python3 scripts/test_mmrl_phase06_diagnostics_contract.py',text)

if __name__=='__main__':
    unittest.main(verbosity=2)
