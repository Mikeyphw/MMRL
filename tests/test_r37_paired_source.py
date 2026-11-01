"""R37 source regression contracts -- stdlib-only, runnable on Termux."""
from pathlib import Path
import importlib.util
import tempfile
import types
import sys
from unittest.mock import patch, Mock
import unittest

ROOT = Path(__file__).resolve().parents[1]


def module():
    spec = importlib.util.spec_from_file_location('mmrl_r37_pair', ROOT/'scripts/mmrl_r37_pair.py')
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class R37SourceContracts(unittest.TestCase):
    def test_hosted_only_build_and_source_boundary(self):
        wf = (ROOT/'.github/workflows/mmrl-r37-paired-device-apks.yml').read_text()
        self.assertIn('./gradlew :app:assembleOfficialDebugAndroidTest', wf)
        self.assertIn('scripts/mmrl_r36_hosted.py run', wf)
        self.assertIn('scripts/mmrl_r36_hosted.py verify', wf)
        self.assertIn('upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02', wf)
        self.assertIn('workflow_dispatch:', wf)
        self.assertIn('GITHUB_SHA', wf)
        self.assertIn('test "${#APKS[@]}" -eq 1', wf)

    def test_pair_rejects_signer_mismatch_and_missing_suite(self):
        m = module()
        sha = 'a'*40
        digest = 'c'*64
        app = {'package':m.APP_PACKAGE,'signer_sha256':digest,'sha256':digest,'size':100}
        test = {'package':m.TEST_PACKAGE,'signer_sha256':digest,'sha256':'d'*64,'size':100}
        manifest = 'E: instrumentation\n A: android:name(0x0)="androidx.test.runner.AndroidJUnitRunner"\n A: android:targetPackage(0x0)="com.mikeyphw.mmrl.debug"'
        plan = {'source_sha':sha,'devtool_sha':m.DEVTOOL_SHA,'release_qualified':False,'device_qualified':False,'graph_sha256':digest}
        verified = {'source_sha':sha,'run_id':123,'attempt':1,'apk_sha256':digest,'signer_sha256':digest,'signature_independently_verified':True,'release_qualified':False,'device_qualified':False,'graph_sha256':digest}
        receipt = {'source_commit':sha,'run_id':'123','attempt':1,'kind':'devtool.actions.node-handoff','schema_version':2,'authenticated':False,'release_qualified':False,'graph_sha256':digest,'workflow_sha256':digest,'files':[{'sha256':digest,'size':100}]}
        good = m.verify_pair(app,test,manifest,plan,verified,receipt,source=sha,run_id=123,attempt=1)
        self.assertEqual(good['status'], 'HOSTED_PAIR_VERIFIED_NON_DEVICE')
        self.assertEqual(good['test_execution'], 'NOT_RUN')
        with self.assertRaisesRegex(ValueError,'signers differ'):
            m.verify_pair(app,{**test,'signer_sha256':'e'*64},manifest,plan,verified,receipt,source=sha,run_id=123,attempt=1)
        with self.assertRaisesRegex(ValueError,'target'):
            m.verify_pair(app,test,manifest.replace('com.mikeyphw.mmrl.debug"','wrong"'),plan,verified,receipt,source=sha,run_id=123,attempt=1)
        with self.assertRaisesRegex(ValueError,'source'):
            m.verify_pair(app,test,manifest,plan,verified,receipt,source='b'*40,run_id=123,attempt=1)

    def test_apk_uses_canonical_fail_closed_certificate_parser(self):
        # Source unit-test: hosted apksigner and aapt execution are exercised
        # by GitHub Actions, never by local Termux Gradle.
        m = module()
        fake_package = types.ModuleType('devtool')
        fake_package.__path__ = []
        fake_actions = types.ModuleType('devtool.actions_android')
        cert = 'a' * 64
        fake_actions._fingerprint = Mock(return_value=cert)
        with tempfile.TemporaryDirectory() as tempdir:
            apk = Path(tempdir) / 'safe.apk'
            apk.write_bytes(b'signed-apk-fixture')
            fake_badging = ("package: name='com.mikeyphw.mmrl.debug' "
                            "versionCode='1' versionName='1.0'\n")
            with patch.dict(sys.modules, {'devtool': fake_package,
                                          'devtool.actions_android': fake_actions}), \
                 patch.object(m, 'run_tool', side_effect=['Signer #1 certificate SHA-256 digest: '+cert,
                                                        fake_badging]) as called:
                info = m.apk_info(apk, m.APP_PACKAGE)
            self.assertEqual(info['signer_sha256'], cert)
            self.assertEqual(info['package'], m.APP_PACKAGE)
            self.assertEqual(info['sha256'], m.file_sha(apk))
            self.assertEqual(called.call_args_list[0].args[:4],
                             ('apksigner', 'verify', '--print-certs', str(apk)))
            fake_actions._fingerprint.assert_called_once_with(
                'Signer #1 certificate SHA-256 digest: '+cert, source='apksigner')

    def test_canonical_certificate_parser_rejection_is_not_bypassed(self):
        m = module()
        fake_package = types.ModuleType('devtool')
        fake_package.__path__ = []
        fake_actions = types.ModuleType('devtool.actions_android')
        fake_actions._fingerprint = Mock(side_effect=ValueError('conflicting digests'))
        with tempfile.TemporaryDirectory() as tempdir:
            apk = Path(tempdir) / 'safe.apk'
            apk.write_bytes(b'signed-apk-fixture')
            with patch.dict(sys.modules, {'devtool': fake_package,
                                          'devtool.actions_android': fake_actions}), \
                 patch.object(m, 'run_tool', return_value='ambiguous'):
                with self.assertRaisesRegex(ValueError, 'conflicting digests'):
                    m.apk_info(apk, m.APP_PACKAGE)

    def test_reviewed_only_three_readonly_smoke_methods(self):
        source=(ROOT/'app/src/androidTest/kotlin/com/dergoogler/mmrl/acceptance/OfficialDebugAcceptanceTest.kt').read_text()
        self.assertEqual(source.count('@Test'), 3)
        for method in ('targetPackageIsOfficialDebugAndInstalled','launcherIntentIsResolvable','launcherActivityCanReachResumedState'):
            self.assertIn(method, source)
        for dangerous in ('Shell.cmd(', 'SQLiteDatabase.openDatabase(', 'pm clear','su -c', 'setScope(', 'toggleModule(', 'requestRoot('):
            self.assertNotIn(dangerous, source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
