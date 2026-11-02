#!/usr/bin/env python3
"""Static regressions for the hosted real-device instrumentation pipeline."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]

class DeviceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gradle = (ROOT/'app/build.gradle.kts').read_text()
        cls.workflow = (ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        cls.test = (ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/LsposedDeviceAcceptanceTest.kt').read_text()
        cls.pair = (ROOT/'scripts/mmrl_lsposed_device_pair.py').read_text()
        cls.runner = (ROOT/'scripts/mmrl_lsposed_device_runner.py').read_text()

    def test_instrumentation_target_and_dependencies(self):
        self.assertIn('testApplicationId = "com.dergoogler.mmrl.debug.test"', self.gradle)
        self.assertIn('testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"', self.gradle)
        self.assertIn('androidTestImplementation("androidx.test.uiautomator:uiautomator:', self.gradle)

    def test_hosted_build_same_job_after_verified_apk(self):
        workflow = self.workflow
        self.assertLess(workflow.index('Verify APK signature package and source-bound native receipt'),
                        workflow.index('Build paired Android instrumentation APK'))
        self.assertIn(':app:assembleOfficialDebugAndroidTest', workflow)
        self.assertIn('mmrl-lsposed-device-test-apk-${{ github.run_id }}-attempt${{ github.run_attempt }}', workflow)
        self.assertIn('scripts/mmrl_lsposed_device_pair.py', workflow)

    def test_on_device_uses_readonly_apis(self):
        for text in ('LsposedDiscovery.scanInstalledApks(', 'LsposedRuntime.inspect()',
                     'mmrl_lsposed_device_acceptance.json', 'INCONCLUSIVE_PROVIDER_UNAVAILABLE',
                     'PASS_VECTOR_DAEMON_AND_MODULE_QUERY', 'UiDevice.getInstance(', 'PlatformManager.isAlive'):
            self.assertIn(text, self.test)
        for forbidden in ('setScope(', 'enableModule(', 'disableModule(', 'reboot(',
                          'clearPackageData', 'am force-stop', 'pm clear', 'uninstall('):
            self.assertNotIn(forbidden, self.test)

    def test_exact_signed_pair_and_no_forged_qualification(self):
        for text in ('sig_main != sig_test', 'source_commit', 'device_qualified\': False',
                     'test_sha256', 'main_sha256', 'android:targetPackage'):
            self.assertIn(text, self.pair)

    def test_runner_does_not_downgrade_inconclusive(self):
        for text in ('gh\', \'run\', \'download', 'run-as', 'PASS_RUNTIME_AND_UI',
                     'NOT_QUALIFIED', 'ro.kernel.qemu', 'head_branch', 'adb(', 'instrument'):
            self.assertIn(text, self.runner)
        for forbidden in ("'uninstall'", "'pm', 'clear'", "'disable-verity'", "'rm', '-rf'"):
            self.assertNotIn(forbidden, self.runner)

if __name__ == '__main__':
    unittest.main(verbosity=2)
