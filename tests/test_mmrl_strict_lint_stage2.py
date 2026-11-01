"""Offline source regressions for 2026-11-01 hosted strict-Lint remediation.

Run with python3 -m unittest discover -s tests -p 'test_mmrl_strict_lint_stage2.py'.
These checks do not replace GitHub-hosted Gradle / Android Lint acceptance.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def source(path):
    return (ROOT / path).read_text(encoding='utf-8')

class StrictLintStage2SourceTests(unittest.TestCase):
    def test_strict_lint_remains_fatal(self):
        gradle = source('app/build.gradle.kts')
        for needle in ('"AndroidGradlePluginVersion",', 'abortOnError = true',
                       'warningsAsErrors = true', 'checkDependencies = true',
                       '"NewerVersionAvailable",'):
            self.assertIn(needle, gradle)
        self.assertIn('Gradle 9.7.1 / AGP 9.3.2', gradle)
        workflow = source('.github/workflows/mmrl-release-seal.yml')
        self.assertIn('-Pmmrl.fullLint=true', workflow)
        self.assertIn('Preserve strict Lint and unit-test reports', workflow)

    def test_compose_sources_track_actual_window(self):
        for path in ('ui/src/main/kotlin/com/dergoogler/mmrl/ui/component/BottomSheet.kt',
                     'ui/src/main/kotlin/com/dergoogler/mmrl/ui/component/scaffold/ScaffoldScope.kt',
                     'ext/src/main/kotlin/com/dergoogler/mmrl/ext/ScreenExt.kt'):
            body = source(path)
            self.assertIn('LocalWindowInfo.current.containerSize', body)
            self.assertNotIn('LocalConfiguration.current', body)
            self.assertIn('LocalDensity.current', body)

    def test_dsl_and_modifier_contracts(self):
        event = source('ext/src/main/kotlin/com/dergoogler/mmrl/ext/EventExt.kt')
        self.assertEqual(event.count('@Suppress("ComposableNaming")'), 2)
        webui = source('webui-core-compat/src/main/kotlin/dev/mmrlx/compose/webui/WebUIState.kt')
        self.assertIn('@Suppress("ComposableNaming")\n@Composable\nfun WebUIState.insets', webui)
        bbcode = source('ui/src/main/kotlin/com/dergoogler/mmrl/ui/component/text/BBCodeText.kt')
        self.assertIn('fun BBCodeText(\n    text: String,\n    modifier: Modifier = Modifier,', bbcode)
        main = source('app/src/main/kotlin/com/dergoogler/mmrl/ui/screens/main/MainScreen.kt')
        self.assertNotIn('contentModifier', main)
        self.assertIn('private fun ExpandedMainLayout(\n    updates: Int,\n    activityAttentionCount: Int,\n    modifier: Modifier,', main)

    def test_locales_keep_single_format_argument(self):
        tests = (
            ('app/src/main/res/values-ta/strings.xml','view_module_install_confirm_desc'),
            ('app/src/main/res/values-ar/strings_terminal.xml','unable_to_gather_module_info_of_file'),
            ('app/src/main/res/values-hi/strings_terminal.xml','unable_to_gather_module_info_of_file'),
        )
        for path,name in tests:
            root = ET.parse(ROOT / path).getroot()
            values = [n.text or '' for n in root.findall('string') if n.attrib.get('name') == name]
            self.assertEqual(len(values),1,path)
            self.assertEqual(values[0].count('%1$s'),1,(path,values))

    def test_database_and_file_storage_contracts(self):
        db=source('app/src/main/kotlin/com/dergoogler/mmrl/lsposed/LsposedScopeRepository.kt')
        self.assertIn('import androidx.core.database.sqlite.transaction',db)
        self.assertIn('db.transaction {',db)
        self.assertNotIn('db.beginTransaction()',db)
        self.assertIn('wal_checkpoint(FULL)',db)
        for name in ('OperationStagingStore','UpdateRollbackStore'):
            text=source(f'app/src/main/kotlin/com/dergoogler/mmrl/installer/{name}.kt')
            self.assertIn('require(hasImmediateFreeSpace(root))',text)
            self.assertIn('@SuppressLint("UsableSpace")',text)
            self.assertIn('directory.usableSpace >= MIN_FREE_BYTES',text)
        compat=source('compat/src/main/kotlin/com/dergoogler/mmrl/compat/MediaStoreCompat.kt')
        self.assertIn('put(MediaStore.MediaColumns.IS_PENDING, if (pending) 1 else 0)',compat)
        webui=source('webui-core-compat/src/main/kotlin/dev/mmrlx/compose/webui/WebUIState.kt')
        self.assertIn('safeBrowsingEnabled = true',webui)
        self.assertNotIn('if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)',webui)

if __name__ == '__main__':
    unittest.main()
