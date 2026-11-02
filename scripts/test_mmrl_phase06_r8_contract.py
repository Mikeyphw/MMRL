#!/usr/bin/env python3
"""R8 offline invariants; no Android commands execute in this test."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]

class R8Contract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.device=(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerLabDeviceTest.kt').read_text()
        cls.host=(ROOT/'scripts/mmrl_phase06_lab_write.py').read_text()
        cls.runner=(ROOT/'scripts/mmrl_phase06_device.py').read_text()
        cls.flow=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
    def test_disposable_lab_only(self):
        for word in ('com.mikeyphw.mmrl.hooklab.module',
                     'com.mikeyphw.mmrl.hooklab.target',
                     'mmrl_phase06_lab_authorization',
                     'AUTHORIZE_DISPOSABLE_LAB_ONLY',
                     'mmrl_phase06_lab_nonce',
                     'mmrl_vector_manager_lab_authorization.token',
                     'ticket.delete()',
                     'unrelated_module_states_unchanged',
                     'report.put("stage", "INTEND_ENABLE")',
                     'report.put("stage", "INTEND_DISABLE")',
                     'VectorManager.change(context, VectorManager.Change(',
                     'expectedEnabled = false', 'expectedEnabled = true'):
            self.assertIn(word,self.device)
        for forbidden in ('"scope", "set"', 'ProcessBuilder(', 'am force-stop',
                          'pm clear', 'uninstall(', 'reboot(', '"android"', '"system"'):
            self.assertNotIn(forbidden,self.device)
    def test_on_device_prewrite_journal_and_restoration(self):
        for token in ('"BASELINE_JOURNALED"', 'commit() // On-device journal',
                      '"RESTORING_LAB"', "'RECOVERY_REQUIRED'",
                      '"PASS_LAB_WRITE_AND_RESTORE"', '"original_scope"',
                      '"original_enabled"', 'scope_writes_attempted',
                      'module_writes_attempted', '"restored"'):
            # RECOVERY_REQUIRED is a host state, not reported by device class.
            self.assertIn(token,self.device+self.host)
        self.assertIn('finally {',self.device)
        self.assertIn('if (authorizedToRestore)',self.device)
        self.assertIn('pending.renameTo(evidence)',self.device)
    def test_host_exact_pair_and_safe_recovery(self):
        for token in ('--authorize-lab-changes','--recover',
                      'secrets.token_hex(24)', 'HOST_TICKET_COMMITTED',
                      'mmrl-phase06-isolated-', 'mmrl-lsposed-hook-lab-',
                      'pair-receipt.json','installed_apk(', 'sha256(path) != sha256(apk)',
                      "args=['adb', 'am', 'instrument']", 'persist(journal_path, journal, create=True)',
                      "journal.get('original_scope') not in ([], [EXPECTED_SCOPE])",
                      "lab.cli(serial, 'modules', 'disable', LAB_MODULE)",
                      "lab.cli(serial, 'scope', 'rm', LAB_MODULE, EXPECTED_SCOPE)",
                      "lab.cli(serial, 'scope', 'add', LAB_MODULE, EXPECTED_SCOPE)",
                      'PASS_LAB_WRITE_AND_RESTORATION',
                      "'RECOVERY_REQUIRED' if not restoration", "'-e', 'class'", CONSENT):
            self.assertIn(token,self.host)
        for forbidden in ('uninstall(', "'uninstall'", "'pm', 'clear'", "'reboot'",
                          'disable-verity', 'adb root'):
            self.assertNotIn(forbidden,self.host)
    def test_runner_verified_reuse_with_no_reinstall(self):
        for token in ('--reuse-installed', 'installed_apk_sha',
                      'refusing unverified installed APK',
                      'resume-', 'if a.reuse_installed:',
                      'main_sha256', 'test_sha256'):
            self.assertIn(token,self.runner)
        self.assertIn('if not a.reuse_installed:',self.runner)
        self.assertNotIn('pm clear',self.runner)
        self.assertNotIn('uninstall(',self.runner)
    def test_ci_contract_and_class_isolated(self):
        self.assertIn('python3 scripts/test_mmrl_phase06_r8_contract.py',self.flow)
        readonly=(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        self.assertNotIn('VectorManager.change(', readonly)
        self.assertNotIn('VectorManagerLabDeviceTest', readonly)

CONSENT = 'AUTHORIZE_DISPOSABLE_LAB_ONLY'

if __name__ == '__main__':
    unittest.main(verbosity=2)
