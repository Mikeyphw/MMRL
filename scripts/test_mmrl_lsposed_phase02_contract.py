#!/usr/bin/env python3
"""Phase 02 source-only regression guards. Gradle/UI acceptance remains hosted CI."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class DiscoverySourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.engine = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedDiscovery.kt').read_text()
        cls.screen = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedDiscoveryScreen.kt').read_text()
        cls.modules = (ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/modules/ModulesScreen.kt').read_text()
        cls.resources = ET.parse(ROOT/'app/src/main/res/values/strings_lsposed_discovery.xml').getroot()

    def test_new_destination_has_reachable_action(self):
        self.assertIn('@Destination<RootGraph>', self.screen)
        self.assertIn('LsposedDiscoveryScreenDestination', self.modules)
        self.assertIn('navigator.navigate(LsposedDiscoveryScreenDestination)', self.modules)

    def test_installed_package_discovery_is_read_only(self):
        for anchor in ['getInstalledApplications(PackageManager.GET_META_DATA)',
                       'META-INF/xposed/java_init.list', 'META-INF/xposed/native_init.list',
                       'assets/xposed_init', 'xposedmodule']:
            self.assertIn(anchor, self.engine)
        for dangerous in ['Runtime.getRuntime(', 'ProcessBuilder(', 'exec(', 'installPackage(',
                          'setApplicationEnabledSetting(', 'delete(', 'mkdir(', 'createNewFile(']:
            self.assertNotIn(dangerous, self.engine)

    def test_root_probe_only_in_explicit_click_handler(self):
        effect = self.screen.split('LaunchedEffect(context) {', 1)[1].split('Scaffold(',1)[0]
        self.assertNotIn('inspectFrameworkModules(', effect)
        self.assertIn('onClick = {', self.screen)
        self.assertIn('LsposedDiscovery.inspectFrameworkModules()', self.screen)
        self.assertIn('withContext(Dispatchers.IO)', self.screen)
        self.assertIn('check(PlatformManager.isAlive)', self.engine)
        self.assertIn('PlatformManager.moduleManager.modules', self.engine)

    def test_framework_status_is_not_claimed_as_activation(self):
        self.assertIn('Installed module status is not runtime status',
                      (ROOT/'app/src/main/res/values/strings_lsposed_discovery.xml').read_text())
        self.assertNotIn('activeHooks', self.engine)

    def test_no_new_system_permissions_and_no_root_mutations(self):
        forbidden = ('su -c', 'scope.set', 'setScope', 'setEnabled', 'toggleModule',
                     'chmod', 'magisk --', 'reboot(', 'rm -rf')
        for fragment in forbidden:
            self.assertNotIn(fragment, self.engine + self.screen)
        self.assertIn('getLaunchIntentForPackage', self.screen)

    def test_strings_do_not_expand_existing_missing_translation_debt(self):
        children = list(self.resources)
        self.assertGreaterEqual(len(children), 12)
        self.assertTrue(all(node.attrib.get('translatable') == 'false' for node in children))
        names = [node.attrib['name'] for node in children]
        self.assertEqual(len(names), len(set(names)))

if __name__ == '__main__':
    unittest.main(verbosity=2)
