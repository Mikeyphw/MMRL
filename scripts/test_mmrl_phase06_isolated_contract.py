#!/usr/bin/env python3
"""Offline source-only checks; all Android/Gradle execution remains in GitHub."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]

class IsolatedContract(unittest.TestCase):
    def test_opt_in_package_variant(self):
        source=(ROOT/'app/build.gradle.kts').read_text()
        for text in ('mmrlPhase06IsolatedTag', 'phase06Tag == null', 'phase06Id ?: mmrlBaseApplicationId',
                     '".debug"', 'phase06Id?.let { "$it.test" }'):
            self.assertIn(text,source)
    def test_dynamic_permission_is_isolated(self):
        source=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertEqual(source.count('${phase06DynamicPermissionId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'),2)
    def test_instrumentation_accepts_isolated_id(self):
        source=(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        self.assertIn('phase06',source)
        self.assertIn('r[0-9]+a[0-9]+',source)
    def test_runner_never_replaces_installed_debug(self):
        source=(ROOT/'scripts/mmrl_phase06_device.py').read_text()
        for item in ('APP_PREFIX', "app_id = f'{APP_PREFIX}.r{a.run_id}a{attempt}'", 'Isolated package already installed',
                     "evidence.get('target_package') == app_id", "evidence.get('module_writes') == 0"):
            self.assertIn(item,source)
        for item in ('pm clear', 'uninstall', 'rm -rf'):
            self.assertNotIn(item,source)
    def test_hosted_job_and_receipt(self):
        workflow=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        for item in ('phase06-isolated:', 'needs: debug-dag', 'mmrlPhase06IsolatedTag',
                     'scripts/mmrl_phase06_isolated_pair.py',
                     'mmrl-phase06-isolated-${{ github.run_id }}-attempt${{ github.run_attempt }}'):
            self.assertIn(item,workflow)

if __name__=='__main__':
    unittest.main(verbosity=2)
