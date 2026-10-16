# MMRL — GitHub Actions through Devtool

## Scope

This overlay adds **one manual GitHub workflow** (`devtool-android-build.yml`) for
`app:officialDebug` APK candidates. Existing `.github/workflows/mmrl-release-seal.yml`
already runs on push and pull requests, with debug, static, release-*validation* and
instrumentation **compilation**. This overlay leaves it untouched.

**Do not treat a green GitHub run as a production release.** In particular, GitHub
release-validation signing uses an ephemeral CI key. Only the repository's Devtool
AOPS04 release workflow with the configured project signing identity, real-device
instrumentation, install and upgrade smoke can qualify a release. The new GitHub
workflow produces a debug APK only, and `device_qualified=false`.

## After applying the Devtool overlay

```sh
cd ~/Code/MMRL  # or your actual MMRL directory
python3 scripts/verify-mmrl-github-actions.py --static
python3 scripts/validate-mmrl-source-hygiene.py
git status --short
git add .github/workflows/devtool-android-build.yml scripts/verify-mmrl-github-actions.py docs/ci/DEVTOOL_GITHUB_ACTIONS.md
git commit -m 'Add Devtool GitHub Actions OfficialDebug build'
git push
```

Configure GitHub CLI authentication once in Termux (`gh auth login`, check
`gh auth status`). Repository permissions must permit workflow dispatch. The
workflow must be pushed to GitHub before calling Devtool. In some setups the
workflow must also exist on the default branch for dispatch to work.

## Devtool dispatch and observation

Run from the Git repository root. Use **the generic** `actions plan/run` path;
MMRL's custom `officialDebug` flavor and multi-module Gradle layout can confuse
Android's simplified module/variant auto-discovery.

```sh
REF="$(git branch --show-current)"
devtool -r "$PWD" actions diagnose --json
devtool -r "$PWD" actions workflows --json
devtool -r "$PWD" actions plan devtool-android-build.yml --ref "$REF" \
  --input project_dir=. --input module=app --input variant=officialDebug \
  --input format=apk --json
devtool -r "$PWD" actions run devtool-android-build.yml --ref "$REF" \
  --input project_dir=. --input module=app --input variant=officialDebug \
  --input format=apk --yes --json
```

The Devtool JSON envelope holds operation data in `.data`. Capture `request_id`
and, once correlated, `run_id`. A dispatch accepted without a correlated run
must **not** be dispatched again blindly; use `actions resume`.

```sh
devtool -r "$PWD" actions resume --request-id REQUEST_ID --json
devtool -r "$PWD" actions runs --limit 10 --json
devtool -r "$PWD" actions view --run-id RUN_ID --json
devtool -r "$PWD" actions jobs --run-id RUN_ID --json
devtool -r "$PWD" actions watch --run-id RUN_ID --interval 5 --max-checks 120 --json
devtool -r "$PWD" actions artifacts --run-id RUN_ID --json
devtool -r "$PWD" actions download --run-id RUN_ID --artifact-id ARTIFACT_ID --json
```

`actions download` preserves the GitHub artifact archive with integrity checks.
Compare the downloaded ZIP member hashes with `build-evidence.json`. Inspect the
signature and manifest using Android SDK 36 `apksigner` and `aapt` before an
install. A successful `actions download` is **not** a device test or release seal.

## Existing automatic CI

Use `mmrl-release-seal.yml` to review push/PR build-test-lint results. It also
accepts manual `workflow_dispatch`, but that workflow does not produce a
Devtool-collected debug artifact under the new candidate schema.

```sh
devtool -r "$PWD" actions plan mmrl-release-seal.yml --ref "$REF" --json
devtool -r "$PWD" actions run mmrl-release-seal.yml --ref "$REF" --yes --json
```

## Github runner prerequisites

Ubuntu 24.04, JDK 21, Gradle wrapper 9.7.1, SDK 36/build-tools 36.0.0,
NDK 29.0.14206865 and CMake 3.22.1. Source, product-boundary, native contracts,
unit tests, lint and Android-test **compilation** must pass before publishing the
OfficialDebug APK plus the JSON receipt. Android device tests **do not run**.

### Limitations and recovery

- If sdkmanager cannot provision NDK r29 on GitHub's x86_64 Linux runner, the
  remote build fails rather than silently using a different NDK.
- A failed Gradle task never uploads a verified APK (diagnostic reports may upload).
- Builds from an unpushed local commit cannot be executed by GitHub; commit/push first.
- No signing credentials or Android device access are sent to GitHub by this overlay.
- Source-bound overlay application requires an exact match to the archived snapshot
  and a clean Git worktree. After upstream changes, regenerate it rather than
  bypassing the guard.
- The current snapshot was checked offline; remote runner execution, artifact
  import and device qualification must be reported only after actual evidence.
