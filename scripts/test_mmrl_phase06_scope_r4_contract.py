#!/usr/bin/env python3
"""R4 regression: special Vector target names are observable, never writable."""
from pathlib import Path
import importlib.util
import unittest

ROOT = Path(__file__).resolve().parents[1]


class R4Contract(unittest.TestCase):
    def test_special_read_names_never_allowed_as_write_targets(self):
        source = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/VectorManager.kt').read_text()
        self.assertIn('valid(pkg) || pkg == "android" || pkg == "system"', source)
        self.assertIn('if (!validScopePackage(pkg) || user !is Number', source)
        self.assertIn('private fun safeTarget', source)
        self.assertIn('require(valid(target))', source)
        self.assertNotIn('"scope", "set"', source)
        self.assertIn('valid(args[3].removeSuffix("/0"))', source)

    def test_special_names_have_no_remove_button(self):
        source = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/VectorManagerPanel.kt').read_text()
        self.assertIn('entry.packageName !in setOf("android", "system")', source)
        self.assertIn('VectorManager.change(context, change)', source)

    def test_read_only_probe_classifies_real_device_issues(self):
        probe_path = ROOT/'scripts/mmrl_phase06_scope_probe.py'
        spec = importlib.util.spec_from_file_location('mmrl_r4_probe', probe_path)
        self.assertIsNotNone(spec)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        rows = [
            {'APP_PACKAGE':'system', 'USER_ID':0},
            {'APP_PACKAGE':'android', 'USER_ID':0},
            {'APP_PACKAGE':'com.example.target', 'USER_ID':0},
        ]
        count, anomalies, special = module.scope_issues(rows)
        self.assertEqual((count, anomalies), (3, []))
        self.assertEqual(len(special), 2)
        self.assertIn('system', special[0])
        self.assertIn('android', special[1])
        for forbidden in ("'scope add'", "'scope rm'", "'modules enable'", "'modules disable'", 'pm clear', 'uninstall('):
            self.assertNotIn(forbidden, probe_path.read_text())

    def test_hosted_contract_and_failure_evidence(self):
        workflow = (ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        self.assertIn('python3 scripts/test_mmrl_phase06_scope_r4_contract.py', workflow)
        device = (ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        for token in ('"stage"', '"error_detail"', '"scope:"', 'report.optString("status") == "PASS_READ_ONLY_INVENTORY"'):
            self.assertIn(token, device)


if __name__ == '__main__':
    unittest.main(verbosity=2)
