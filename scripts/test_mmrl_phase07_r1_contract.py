#!/usr/bin/env python3
"""Phase07 R1 source contracts: diagnostics / UX must remain read-only by default."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
TRANS=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/VectorRootTransport.kt').read_text()
PANEL=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/lsposed/VectorManagerPanel.kt').read_text()
BACK=(ROOT/'app/src/main/kotlin/com/dergoogler/mmrl/lsposed/VectorManager.kt').read_text()
XML=ET.parse(ROOT/'app/src/main/res/values/strings_lsposed_manage.xml').getroot()
FLOW=(ROOT/'.github/workflows/mmrl-lsposed-devtool.yml').read_text()

class Phase07R1Contract(unittest.TestCase):
    def test_bind_failures_have_specific_bounded_codes(self):
        for code in ('BIND_DISPATCH_FAILED','BIND_INTERRUPTED','BIND_TIMEOUT','BIND_FAILED'):
            self.assertIn('[%s]' % code,TRANS)
        self.assertIn('ready.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)',TRANS)
        self.assertIn('Thread.currentThread().interrupt()',TRANS)
        self.assertIn('RootService.bind(',TRANS)
        self.assertIn('RootService.unbind(',TRANS)
        self.assertNotIn('ProcessBuilder(',TRANS)
        self.assertNotIn('"su"',TRANS)
        self.assertNotIn('"sh"',TRANS)
    def test_search_and_count_do_not_touch_configuration(self):
        for token in ('moduleQuery = it.take(80)', 'module.label.contains(term, ignoreCase = true)',
                      'module.packageName.contains(term, ignoreCase = true)',
                      'data.modules.count { it.enabled }', 'visibleModules.forEach { module ->'):
            self.assertIn(token,PANEL)
        self.assertNotIn('LaunchedEffect(',PANEL)
        self.assertIn('VectorManager.change(context, change)',PANEL)
        self.assertIn('AlertDialog(',PANEL)
    def test_target_input_cleared_when_switching_modules(self):
        self.assertIn('selected = modulePackage\n        currentScope = null\n        target = ""',PANEL)
        self.assertIn('selected = null\n        currentScope = null\n        target = ""',PANEL)
    def test_root_help_does_not_claim_rollback(self):
        names={x.attrib.get('name'):x.text or '' for x in XML}
        for item in ('lsposed_manage_root_bind_help','lsposed_manage_module_count',
                     'lsposed_manage_search_modules','lsposed_manage_no_matches'):
            self.assertIn(item,names)
        self.assertIn('failed request may still have changed saved configuration',
                      names['lsposed_manage_root_bind_help'])
        self.assertIn('failure.contains("[BIND_")',PANEL)
    def test_phase06_guards_unchanged(self):
        for token in ('VectorRootTransport.execute(context, operation, module, target)', 'expectedEnabled', 'expectedScope',
                      'safeTarget(context.applicationContext, pkg)',
                      'Vector read-back differs', 'if (request.action == Action.ADD_SCOPE && !before.enabled)'):
            self.assertIn(token, BACK)
        self.assertNotIn('"scope", "set"', BACK)
    def test_hosted_ci_runs_contract(self):
        self.assertIn('python3 scripts/test_mmrl_phase07_r1_contract.py',FLOW)

if __name__=='__main__':unittest.main(verbosity=2)
