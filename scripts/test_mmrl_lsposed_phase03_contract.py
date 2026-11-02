#!/usr/bin/env python3
"""Phase 03 source/read-only invariants (Android compilation happens via hosted Devtool)."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedRuntime.kt'
SCREEN=ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/LsposedDiscoveryScreen.kt'
XML=ROOT/'app/src/main/res/values/strings_lsposed_runtime.xml'

class Phase03Contracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.src=SRC.read_text()
        cls.screen=SCREEN.read_text()
        cls.xml=ET.parse(XML).getroot()
    def test_explicit_opt_in_and_background_io(self):
        self.assertIn('LsposedRuntime.inspect()',self.screen)
        self.assertIn('withContext(Dispatchers.IO)',self.screen)
        effect=self.screen.split('LaunchedEffect(context) {',1)[1].split('Scaffold(',1)[0]
        self.assertNotIn('LsposedRuntime.inspect()',effect)
        self.assertIn('enabled = !checkingRuntime',self.screen)
    def test_exact_cli_allowlist_only(self):
        self.assertIn('require(command == "status" || command == "modules ls")',self.src)
        self.assertIn('"/data/adb/modules/zygisk_vector/cli"',self.src)
        self.assertIn('ProcessBuilder("su", "-c", "$CLI --json $command")',self.src)
        for forbidden in ['scope add','scope set','scope rm','modules enable','modules disable',
                          'config set','db reset','reboot','install-module','Runtime.getRuntime()']:
            self.assertNotIn(forbidden,self.src)
    def test_runtime_truth_table_not_install_equals_active(self):
        self.assertIn('if (vector == null)',self.src)
        self.assertIn('if (vector.state != "ENABLE")',self.src)
        self.assertIn('daemonResponded: Boolean',self.src)
        self.assertIn('Process injection and active hooks remain unverified',self.src)
        self.assertIn('Legacy LSPosed has no compatible public',self.src)
    def test_bounded_root_probe(self):
        for token in ['TIMEOUT_SECONDS = 10L','MAX_OUTPUT_BYTES = 131072',
                      'waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)',
                      'process.destroyForcibly()', 'executor.shutdownNow()',
                      'MAX_MODULES = 2048']:
            self.assertIn(token,self.src)
    def test_json_parser_rejects_non_daemon_and_bad_configuration(self):
        for token in ['!root.optBoolean("success")','optJSONObject("data")',
                      'optJSONArray("data")','state != "enabled" && state != "disabled"',
                      'throw IOException("Unknown Vector module state")']:
            self.assertIn(token,self.src)
    def test_no_broad_permissions_or_untranslated_resource_debt(self):
        self.assertEqual(self.xml.tag,'resources')
        self.assertGreaterEqual(len(self.xml),12)
        self.assertTrue(all(x.get('translatable')=='false' for x in self.xml))
        names=[x.get('name') for x in self.xml]
        self.assertEqual(len(names),len(set(names)))
    def test_no_implicit_scope_mutations_from_screen(self):
        for token in ['setScope(', 'setEnabled(', 'reboot(', 'ProcessBuilder(', 'Runtime.getRuntime(',
                      'am start', 'su -c']:
            self.assertNotIn(token,self.screen)
        self.assertIn('R.string.lsposed_runtime_notice',self.screen)

if __name__=='__main__':unittest.main(verbosity=2)
