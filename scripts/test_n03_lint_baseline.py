"""Verify the exact historical N03 lint baseline without executing Gradle locally."""
from __future__ import annotations
from collections import Counter
from pathlib import Path
import hashlib
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BASELINE = ROOT / "app/lint-baseline.xml"
N03_REPORT_SHA256 = "e56736383d1902e0fb6e62913e08fd31c6495b9f94ecc8c289f8b4f1aef30958"


class N03LintBaselineContract(unittest.TestCase):
    def test_baseline_is_complete_and_portable(self):
        root = ET.fromstring(BASELINE.read_bytes())
        self.assertEqual(root.tag, "issues")
        issues = root.findall("issue")
        self.assertEqual(len(issues), 536)
        self.assertEqual(Counter(i.get("severity") for i in issues),
                         {"Error": 302, "Warning": 232, "Hint": 2})
        self.assertTrue(all(i.get("id") and i.get("message") for i in issues))
        paths = [loc.get("file", "") for issue in issues for loc in issue.findall("location")]
        self.assertTrue(paths)
        self.assertTrue(all(path.startswith(("src/", "../gradle/")) or path == "build.gradle.kts" for path in paths))
        self.assertTrue(all("/home/runner/" not in path for path in paths))
        self.assertTrue(any(path.startswith("../gradle/") for path in paths))
        self.assertFalse(any(path.startswith("/") for path in paths))

    def test_baseline_only_in_hosted_native_android_job(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/next-devtool-native.yml").read_text()
        self.assertEqual(gradle.count('baseline = file("lint-baseline.xml")'), 1)
        for guard in ('System.getenv("MMRL_NEXT_DEVTOOL") == "1"',
                      'System.getenv("GITHUB_ACTIONS") == "true"',
                      'System.getenv("GITHUB_REPOSITORY") == "Mikeyphw/MMRL"'):
            self.assertIn(guard, gradle)
        self.assertNotIn("abortOnError = false", gradle)
        self.assertNotIn("checkOnly +=", gradle)
        self.assertIn("scripts/test_n03_lint_baseline.py", workflow)
        self.assertIn("scripts/hosted_devtool_next.py run --node lint", workflow)

    def test_baseline_origin_documented(self):
        doc = (ROOT / "docs/N03_LINT_BASELINE.md").read_text()
        self.assertIn(N03_REPORT_SHA256, doc)
        self.assertIn("42159873601", doc)
        self.assertIn("302 errors", doc)
        self.assertIn("new", doc.lower())


if __name__ == "__main__":
    unittest.main()
