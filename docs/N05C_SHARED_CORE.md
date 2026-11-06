# N05C — MMRL `next` shared Kotlin core

- Base: `Mikeyphw/MMRL:next` at `992509cf4a030b08a3725a313678f280c01eac3c`.
- Shared library: `Mikeyphw/MMRL-Shared:main` pinned at gitlink `9e09e1267099ef6a015f0c4bb1dcd31e3471d0ee`.
- MMRL-LSPosed N05B merged as PR #16, providing a tested AGP 9 Kotlin source collection precedent.
- `:shared-contracts` is a separate AGP 9 Android library, using only the shared repository's pure Kotlin production sources.
- The first-party release metadata API is not a signing, artifact attestation, or download-authorization mechanism.
- Generic MMRL third-party module downloads, install paths, root services, LSPosed/Vector isolation, and the official release guard are unchanged.
- Gradle 9.1 wrapper / AGP 9 native Kotlin; JDK 21; Java 17 bytecode for the isolated shared library module.
- All Gradle and Android work runs on GitHub Actions; no local Termux Gradle tasks.
- N03 hosted Devtool retains its three native Android nodes, officialDebug package, and fail-closed receipt verification.
- CI workflows must clone submodules recursively. If the Git submodule cannot be fetched, source-contract checks fail.
- Upstream MMRLApp/MMRL:master sync still uses `scripts/sync-upstream-next.zsh`, in independent PRs into `next`. Never merge the old fork `master` into `next`.

## Acceptance

Run `python3 -m unittest scripts/test_n03_source_contract.py scripts/test_n03_lint_baseline.py scripts/test_n05c_shared_source.py -v` on GitHub Actions.
Require `N03 MMRL Next Native Devtool Android CI` and `MMRL Next - Android source build (no release)` green on the PR head.
Production signing, runtime device behavior and upstream cherry-pick compatibility are NOT established by these tests.
