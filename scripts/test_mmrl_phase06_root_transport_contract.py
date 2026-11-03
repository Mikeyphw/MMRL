#!/usr/bin/env python3
"""Phase06 R7 strict source contracts: privileged Vector IPC, never a general root shell."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'app/src/main/kotlin/com/dergoogler/mmrl/lsposed'

class R7TransportContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = (APP/'VectorCliRootService.kt').read_text()
        cls.transport = (APP/'VectorRootTransport.kt').read_text()
        cls.backend = (APP/'VectorManager.kt').read_text()
        cls.device = (ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        cls.aidl = (ROOT/'platform/src/main/aidl/com/dergoogler/mmrl/platform/stub/IVectorCli.aidl').read_text()
        cls.panel = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/VectorManagerPanel.kt').read_text()
    def test_root_process_executes_fixed_cli_directly(self):
        self.assertIn('class VectorCliRootService : RootService()', self.service)
        self.assertIn('Process.myUid() != 0', self.service)
        self.assertIn('ProcessBuilder(listOf(CLI, "--json") + args)', self.service)
        self.assertIn('MAX_BYTES = 131072', self.service)
        self.assertIn('TIMEOUT_SECONDS = 12L', self.service)
        self.assertIn('process.destroyForcibly()', self.service)
        self.assertNotIn('"su"', self.service)
        self.assertNotIn('"sh"', self.service)
        self.assertNotIn('Runtime.getRuntime', self.service)
        self.assertNotIn('scope set', self.service)
    def test_narrow_root_operation_allowlist(self):
        for op in ('"status"','"modules ls"','"modules enable"','"modules disable"',
                   '"scope ls"','"scope add"','"scope rm"'):
            self.assertIn(op,self.service)
        self.assertIn('target.endsWith("/0")',self.service)
        self.assertIn('valid(target.removeSuffix("/0"))',self.service)
        self.assertIn('mmrl_transport_error',self.service)
        self.assertIn('String execute(String operation, String modulePackage, String targetPackage);',self.aidl)
        for forbidden in ('String shell(', 'String runCommand(', 'String execute(String command);'):
            self.assertNotIn(forbidden,self.aidl)
    def test_client_uses_libsu_binder_off_main(self):
        for text in ('RootService.bind(', 'RootService.unbind(', 'IVectorCli.Stub.asInterface(',
                     'CONNECT_TIMEOUT_SECONDS = 20L','CountDownLatch(', 'Looper.myLooper()',
                     'VectorCliRootService::class.java', 'remote.execute(operation, module, target)'):
            self.assertIn(text,self.transport)
        self.assertNotIn('ProcessBuilder(',self.transport)
        self.assertIn('VectorRootTransport.execute(context,',self.backend)
        self.assertNotIn('ProcessBuilder("su"',self.backend)
    def test_all_reads_and_mutations_route_through_context(self):
        for needle in ('private fun supported(context: Context)',
                       'fun scopes(context: Context, modulePackage: String)',
                       'private fun readScope(context: Context, modulePackage: String)',
                       'val pre = inspect(context)', 'val after = inspect(context)',
                       'safeTarget(context.applicationContext, pkg)',
                       'require(valid(target))'):
            self.assertIn(needle,self.backend)
        self.assertIn('VectorManager.scopes(context, modulePackage)',self.panel)
        self.assertIn('VectorManager.scopes(context, item.packageName)',self.device)
        self.assertNotIn('VectorManager.change(',self.device)
        self.assertIn('report.optString("status") == "PASS_READ_ONLY_INVENTORY"',self.device)
    def test_ci_checks_transport(self):
        flow=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        self.assertIn('python3 scripts/test_mmrl_phase06_root_transport_contract.py',flow)

if __name__ == '__main__':
    unittest.main(verbosity=2)
