#!/usr/bin/env python3
"""Offline Phase 05 source-only safety/contract checks (Gradle runs on GitHub)."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
T = 'com.mikeyphw.mmrl.hooklab.target'
M = 'com.mikeyphw.mmrl.hooklab.module'

class HookLabContracts(unittest.TestCase):
    def test_modern_module_exact_static_scope(self):
        base = ROOT/'hook-lab-module/src/main/resources/META-INF/xposed'
        self.assertEqual((base/'scope.list').read_text(), T+'\n')
        self.assertEqual((base/'java_init.list').read_text(), M+'.LabHookModule\n')
        prop = (base/'module.prop').read_text()
        for line in ('minApiVersion=102', 'targetApiVersion=102', 'staticScope=true'):
            self.assertIn(line+'\n', prop)
        self.assertEqual(len((base/'scope.list').read_text().splitlines()), 1)

    def test_exact_method_hook_and_no_generalized_hooking(self):
        p = ROOT/'hook-lab-module/src/main/java/com/mikeyphw/mmrl/hooklab/module/LabHookModule.java'
        s = p.read_text()
        for anchor in ('TARGET.equals(param.getPackageName())', 'param.isFirstPackage()',
                       'getDeclaredMethod("readValue")', 'hook(method).intercept(chain -> "HOOKED_PHASE05")'):
            self.assertIn(anchor, s)
        for unsafe in ('com.android.systemui', 'android.permission', 'system_server', 'Runtime.getRuntime',
                       'ProcessBuilder(', 'scope.add', 'setScope', 'reboot'):
            self.assertNotIn(unsafe, s)

    def test_target_read_only_probe(self):
        base = ROOT/'hook-lab-target/src/main'
        root = ET.parse(base/'AndroidManifest.xml').getroot()
        ns = '{http://schemas.android.com/apk/res/android}'
        provider = root.find('./application/provider')
        self.assertEqual(provider.get(ns+'authorities'), T)
        self.assertEqual(provider.get(ns+'exported'), 'true')
        src = (base/'java/com/mikeyphw/mmrl/hooklab/target/ProbeProvider.java').read_text()
        self.assertIn('ProbeValue.readValue()', src)
        self.assertIn('"/probe".equals(uri.getPath())', src)
        self.assertEqual(src.count('throw new UnsupportedOperationException("read only")'), 3)
        self.assertIn('ORIGINAL_PHASE05', (base/'java/com/mikeyphw/mmrl/hooklab/target/ProbeValue.java').read_text())

    def test_device_root_mutation_allowlist_and_recovery(self):
        s = (ROOT/'scripts/mmrl_phase05_device.py').read_text()
        for key in ('--authorize-lab-changes', '--recover', 'journal.json',
                    "'modules', 'disable', MODULE", "'scope', 'rm', MODULE, TARGET+'/0'",
                    'original_scope', 'RESTORED', 'RECOVERY_REQUIRED',
                    'PASS_HOOK_AND_RESTORATION', 'original_module_state',
                    "'am', 'force-stop', TARGET", 'ro.kernel.qemu'):
            self.assertIn(key, s)
        for forbidden in ('pm clear', 'uninstall', 'reboot', 'magisk --', 'setenforce',
                          'rm -rf', 'setprop', 'disable-verity'):
            self.assertNotIn(forbidden, s)

    def test_ci_builds_pair_after_existing_devtool_gate(self):
        w = (ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        self.assertIn('needs: debug-dag', w)
        self.assertIn('python3 scripts/test_mmrl_phase05_contract.py', w)
        self.assertIn(':hook-lab-target:assembleDebug :hook-lab-module:assembleDebug', w)
        self.assertIn('python3 scripts/mmrl_phase05_pair.py', w)
        self.assertIn('mmrl-lsposed-hook-lab-${{ github.run_id }}-attempt${{ github.run_attempt }}', w)
        self.assertIn('MMRL_PR_HEAD_SHA: ${{ github.event.pull_request.head.sha || github.sha }}', w)
        self.assertIn('head_sha', (ROOT/'scripts/mmrl_phase05_pair.py').read_text())
        g = (ROOT/'settings.gradle.kts').read_text()
        self.assertIn('include(":hook-lab-target", ":hook-lab-module")', g)
        l = (ROOT/'gradle/libs.versions.toml').read_text()
        self.assertIn('libxposedApi = "102.0.0"', l)
        self.assertIn('libxposed-api = { module = "io.github.libxposed:api", version.ref = "libxposedApi" }', l)

if __name__ == '__main__':
    unittest.main(verbosity=2)
