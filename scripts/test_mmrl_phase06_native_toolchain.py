#!/usr/bin/env python3
"""Offline R6 source and toolchain regression contracts; never runs Gradle."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load_checker():
    path = Path(__file__).with_name('mmrl_phase06_native_toolchain.py')
    spec = importlib.util.spec_from_file_location('mmrl_phase06_native_toolchain', path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


class Phase06NativeToolchainContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.checker = load_checker()

    def install_stub(self, root: Path, *, ndk='29.0.14206865', cmake='3.22.1'):
        for kind, pinned, detected, binpath in (
            ('ndk', self.checker.NDK, ndk, 'ndk-build'),
            ('cmake', self.checker.CMAKE, cmake, 'bin/cmake'),
        ):
            path = root / kind / pinned
            (path / binpath).parent.mkdir(parents=True)
            (path / binpath).write_text('stub')
            (path / 'source.properties').write_text(f'Pkg.Revision = {detected}\n')

    def test_exact_native_toolchain_passes(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.install_stub(root)
            self.checker.verify(root)

    def test_mismatched_revision_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.install_stub(root, ndk='28.2.13676358')
            with self.assertRaisesRegex(RuntimeError, 'revision mismatch'):
                self.checker.verify(root)

    def test_partial_ndk_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.install_stub(root)
            (root / 'ndk' / self.checker.NDK / 'source.properties').unlink()
            with self.assertRaisesRegex(RuntimeError, 'Missing SDK package'):
                self.checker.verify(root)

    def test_missing_cmake_binary_refused(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            self.install_stub(root)
            (root / 'cmake' / self.checker.CMAKE / 'bin/cmake').unlink()
            with self.assertRaisesRegex(RuntimeError, 'Incomplete native toolchain'):
                self.checker.verify(root)

    def test_hosted_gradle_pin_matches_both_ci_installations(self):
        gradle = (ROOT / 'platform/build.gradle.kts').read_text()
        workflow = (ROOT / '.github/workflows/mmrl-lsposed-devtool.yml').read_text()
        ndk = self.checker.NDK
        cmake = self.checker.CMAKE
        self.assertIn('if (providers.gradleProperty("mmrlLsposedHosted").orNull == "true") {\n        ndkVersion = "'+ndk+'"', gradle)
        self.assertEqual(workflow.count(f"sdkmanager --install 'ndk;{ndk}' 'cmake;{cmake}'"), 2)
        self.assertEqual(workflow.count('python3 scripts/mmrl_phase06_native_toolchain.py --sdk-root "$ANDROID_SDK_ROOT"'), 2)
        self.assertIn("ORG_GRADLE_PROJECT_mmrlLsposedHosted: 'true'", workflow)
        self.assertIn('-PmmrlLsposedHosted=true', workflow)
        self.assertIn('python3 scripts/test_mmrl_phase06_native_toolchain.py', workflow)


if __name__ == '__main__':
    unittest.main(verbosity=2)
