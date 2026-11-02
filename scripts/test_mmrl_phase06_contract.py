#!/usr/bin/env python3
"""Static Phase 06 safety checks. No local Gradle compilation."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

class Phase06Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.backend=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/VectorManager.kt').read_text()
        cls.panel=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/VectorManagerPanel.kt').read_text()
        cls.screen=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedDiscoveryScreen.kt').read_text()
        cls.device=(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt').read_text()
        cls.runner=(ROOT/'scripts/mmrl_phase06_device.py').read_text()

    def test_fixed_cli_and_no_global_operations(self):
        for anchor in ('"modules", "ls"', '"scope", "ls"', '"enable", "disable"',
                       '"add", "rm"', 'TIMEOUT = 12L', 'MAX_BYTES = 131072',
                       'ProcessBuilder("su", "-c", command)', 'Vector API 102'):
            self.assertIn(anchor,self.backend)
        for bad in ('"scope", "set"', '"db",', '"config",', 'reboot(', 'force-stop',
                    'Runtime.getRuntime(', 'ProcessBuilder("sh"', 'pm clear'):
            self.assertNotIn(bad,self.backend)
        self.assertIn('valid(args[3].removeSuffix("/0"))',self.backend)

    def test_read_back_stale_state_and_target_guard(self):
        for anchor in ('expectedEnabled','expectedScope','pre = inspect(context)',
                       'originalScope = readScope(request.modulePackage)',
                       'after = inspect(context)','newScope = readScope(request.modulePackage)',
                       'System, manager, and MMRL targets are excluded',
                       'ApplicationInfo.FLAG_SYSTEM', 'only', 'REFRESH AND VERIFY'):
            self.assertIn(anchor,self.backend)

    def test_ui_requires_explicit_confirmation(self):
        for anchor in ('VectorManagerPanel()', 'VectorManager.change(context, change)',
                       'AlertDialog(', 'pending = VectorManager.Change(',
                       'withContext(Dispatchers.IO)', 'VectorManager.scopes(',
                       'overview = null', 'lsposed_manage_disclaimer'):
            self.assertIn(anchor, self.screen+self.panel)
        self.assertNotIn('VectorManager.change(', self.screen)
        self.assertNotIn('LaunchedEffect(', self.panel)

    def test_instrumentation_read_only_and_separate_qualification(self):
        self.assertIn('VectorManager.inspect(context)',self.device)
        self.assertIn('VectorManager.scopes(item.packageName)',self.device)
        self.assertIn('PASS_READ_ONLY_INVENTORY',self.device)
        for bad in ('VectorManager.change(', 'scope add', 'modules enable'):
            self.assertNotIn(bad,self.device)
        for anchor in ('pair-receipt.json', 'target=','instrument',
                       'PASS_READ_ONLY_MANAGER_CONFIGURATION','device_qualified',
                       'ro.kernel.qemu', 'module_writes', 'scope_writes'):
            self.assertIn(anchor,self.runner)

    def test_resources_and_ci(self):
        xml=ROOT/'app/src/main/res/values/strings_lsposed_manage.xml'
        root=ET.parse(xml).getroot()
        self.assertTrue(all(x.attrib.get('translatable')=='false' for x in root))
        self.assertGreater(len(root),10)
        workflow=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        self.assertIn('python3 scripts/test_mmrl_phase06_contract.py',workflow)
        self.assertIn('assembleOfficialDebugAndroidTest',workflow)
        self.assertIn('VectorManagerDeviceTest.kt',str(ROOT/'app/src/androidTest/java/com/dergoogler/mmrl/lsposed/VectorManagerDeviceTest.kt'))

if __name__=='__main__':
    unittest.main(verbosity=2)
