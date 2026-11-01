"""Source-only guardrails for MMRL strict hosted lint evidence (no Gradle)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / ".github/workflows/mmrl-release-seal.yml").read_text()
POLICY = (ROOT / "platform/src/main/kotlin/com/dergoogler/mmrl/platform/file/PrivilegedPathPolicy.kt").read_text()


def test_strict_full_lint_cannot_be_skipped():
    assert "- name: Official debug unit tests" in WORKFLOW
    assert "run: ./gradlew :app:testOfficialDebugUnitTest --build-cache --console=plain" in WORKFLOW
    assert "- name: Official debug strict lint" in WORKFLOW
    assert "if: ${{ !cancelled() && matrix.task-set == 'debug' }}" in WORKFLOW
    assert "run: ./gradlew :app:lintOfficialDebug -Pmmrl.fullLint=true --build-cache --console=plain" in WORKFLOW
    assert "- name: Preserve strict Lint and unit-test reports" in WORKFLOW
    assert "if: ${{ always() && matrix.task-set == 'debug' }}" in WORKFLOW
    assert "app/build/intermediates/lint_intermediate_text_report/" in WORKFLOW
    assert "app/build/reports/lint-results-*" in WORKFLOW
    assert "app/build/reports/tests/testOfficialDebugUnitTest/" in WORKFLOW
    assert 'cat "$report"' in WORKFLOW
    assert "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02" in WORKFLOW
    assert "continue-on-error" not in WORKFLOW
    assert "lintBaseline" not in WORKFLOW


def test_privileged_path_allowlist_preserved_and_localized_exception():
    assert POLICY.count('"/sdcard"') == 2
    assert POLICY.count('@Suppress("SdCardPath")') == 2
    read_roots = POLICY.split("private val readRoots =", 1)[1].split("private val mutationRoots =", 1)[0]
    mutation_roots = POLICY.split("private val mutationRoots =", 1)[1].split("fun resolve(", 1)[0]
    for fragment in (read_roots, mutation_roots):
        assert '"/data/adb"' in fragment
        assert '"/data/data"' in fragment
        assert '"/sdcard"' in fragment
        assert '"/storage"' in fragment
        assert 'map(Paths::get)' in fragment
    assert "assertNoSymlinkComponents(root, normalized, allowFinalSymlink)" in POLICY
    assert 'require(!isPseudoFdPath(normalized))' in POLICY


if __name__ == "__main__":
    test_strict_full_lint_cannot_be_skipped()
    test_privileged_path_allowlist_preserved_and_localized_exception()
    print("PASS: strict lint CI evidence and privileged-path boundaries (source only)")
