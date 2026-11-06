"""N05C source lock and safety checks for GitHub Actions and upstream synchronization."""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parent.parent
PIN = "9e09e1267099ef6a015f0c4bb1dcd31e3471d0ee"
SUB = "third_party/mmrl-shared"

class N05CContracts(unittest.TestCase):
    def test_gitlink_matches_public_shared_source(self):
        ls = subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "--stage", "--", SUB], text=True).strip()
        self.assertEqual(ls, f"160000 {PIN} 0	{SUB}")
        local = ROOT / SUB
        self.assertTrue((local / ".git").exists(), "Recursive Git submodule checkout required")
        sha = subprocess.check_output(["git", "-C", str(local), "rev-parse", "HEAD"], text=True).strip()
        self.assertEqual(sha, PIN)
        modules = (ROOT / ".gitmodules").read_text()
        self.assertIn("url = https://github.com/Mikeyphw/MMRL-Shared.git", modules)
        self.assertIn(f"path = {SUB}", modules)

    def test_gradle_uses_pinned_kotlin_sources_without_copying(self):
        adapter = (ROOT / "integration/shared-contracts/build.gradle.kts").read_text()
        settings = (ROOT / "settings.gradle.kts").read_text()
        app = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('include(":shared-contracts")', settings)
        self.assertIn('implementation(project(":shared-contracts"))', app)
        self.assertIn('testImplementation(libs.junit)', app)
        self.assertIn('kotlin.directories.add(', adapter)
        self.assertIn('third_party/mmrl-shared/src/main/kotlin', adapter)
        self.assertNotIn('java.srcDir(', adapter)
        self.assertNotIn('mavenLocal()', adapter)

    def test_hosted_ci_uses_recursive_submodules_and_runs_tests(self):
        for workflow in ("next-devtool-native.yml", "next-android-ci.yml"):
            data = (ROOT / ".github/workflows" / workflow).read_text()
            self.assertIn("submodules: recursive", data)
        native = (ROOT / ".github/workflows/next-devtool-native.yml").read_text()
        self.assertIn("scripts/test_n05c_shared_source.py", native)
        test = (ROOT / "app/src/test/java/com/dergoogler/mmrl/network/N05CSharedCoreTest.kt").read_text()
        self.assertIn("firstPartyUrlBindingRejectsForeignOrModifiedAssets", test)
        self.assertIn("workflowMetadataRequiresExactRunAttemptAndCommit", test)

    def test_policy_is_first_party_metadata_only(self):
        first = (ROOT / "app/src/main/kotlin/com/dergoogler/mmrl/network/FirstPartyReleaseMetadata.kt").read_text()
        self.assertIn('REPOSITORY = "Mikeyphw/MMRL"', first)
        self.assertIn("ReleaseAssetIdentity.of(", first)
        self.assertIn("Sha256Digest.parse(", first)
        self.assertNotIn("Runtime.getRuntime", first)
        self.assertNotIn("su -c", first)
        self.assertNotIn("Vector", first)
        release = (ROOT / ".github/workflows/release-ci.yml").read_text()
        self.assertIn("github.repository == 'MMRLApp/MMRL'", release)

if __name__ == "__main__":
    unittest.main()
