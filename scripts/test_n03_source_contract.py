"""Offline source invariants for the N03 rebuilt-MMRL Devtool integration."""
from __future__ import annotations

from pathlib import Path
import tomllib
import unittest

ROOT = Path(__file__).resolve().parent.parent


class N03SourceContract(unittest.TestCase):
    def setUp(self):
        self.config = tomllib.loads((ROOT / ".devtool.toml").read_text())
        self.workflow = (ROOT / ".github/workflows/next-devtool-native.yml").read_text()
        self.gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.helper = (ROOT / "scripts/hosted_devtool_next.py").read_text()

    def test_target_is_generic_and_independent(self):
        self.assertEqual(self.config["workspace"]["default_target"], "mmrl_next")
        target = self.config["targets"]["mmrl_next"]
        self.assertEqual(target["runner"], "android")
        self.assertEqual(len(target["workflows"]["ci"]), 3)
        for step in target["workflows"]["ci"]:
            self.assertEqual(step["depends_on"], [])
        self.assertEqual(sorted(target["jobs"]), ["assemble_debug", "lint_debug", "unit_debug"])

    def test_three_admitted_native_jobs(self):
        target = self.config["targets"]["mmrl_next"]
        expected = {"assemble_debug": ":app:assembleOfficialDebug",
                    "lint_debug": ":app:lintOfficialDebug",
                    "unit_debug": ":app:testOfficialDebugUnitTest"}
        for job_name, task in expected.items():
            job = target["jobs"][job_name]
            self.assertEqual(job["gradle_tasks"], [task])
            self.assertEqual(job["metadata"]["ci_android_debug_variant"], "officialDebug")
            self.assertEqual(job["metadata"]["ci_toolchain"], "android-gradle")
            self.assertEqual(job["metadata"]["ci_android_java_version"], "21")
        self.assertEqual(target["jobs"]["assemble_debug"]["outputs"],
                         ["app/build/outputs/apk/official/debug/MMRL-Next-CI-OfficialDebug.apk"])
        self.assertEqual(target["jobs"]["lint_debug"]["outputs"], [])
        self.assertEqual(target["jobs"]["unit_debug"]["outputs"], [])

    def test_hosted_only_protected_output_name(self):
        self.assertIn('System.getenv("MMRL_NEXT_DEVTOOL") == "1"', self.gradle)
        self.assertIn('variant.name == "officialDebug"', self.gradle)
        self.assertIn('"MMRL-Next-CI-OfficialDebug.apk"', self.gradle)
        self.assertIn('"MMRL-${vName}-${variant.buildType ?: variant.name}.apk"', self.gradle)

    def test_actions_uses_devtool_native_runner_not_direct_gradle(self):
        self.assertIn('DEVTOOL_SHA: bee6e0b7e49aaaa0c56f755fd5be434a27c52b6c', self.workflow)
        self.assertIn("MMRL_NEXT_DEVTOOL: '1'", self.workflow)
        self.assertIn('packages: \'platforms;android-36 build-tools;36.0.0 platform-tools\'', self.workflow)
        self.assertIn('scripts/hosted_devtool_next.py run --node apk', self.workflow)
        self.assertIn('scripts/hosted_devtool_next.py run --node lint', self.workflow)
        self.assertIn('scripts/hosted_devtool_next.py run --node test', self.workflow)
        self.assertNotIn('run: ./gradlew', self.workflow)
        self.assertNotIn('secrets.', self.workflow)
        self.assertNotIn('workflow_dispatch', self.workflow)

    def test_helper_refuses_local_node_execution(self):
        self.assertIn('hosted_identity(root)', self.helper)
        self.assertIn('from devtool.actions_graph_export import run_node', self.helper)
        self.assertIn('return 2', self.helper)
        self.assertIn('"release_qualified": False', self.helper)

    def test_lost_privileged_code_not_reintroduced(self):
        for value in ("Lsposed", "VectorManager", "hook-lab", "LSPosed"):
            self.assertNotIn(value, (ROOT / ".devtool.toml").read_text())


if __name__ == "__main__":
    unittest.main()
