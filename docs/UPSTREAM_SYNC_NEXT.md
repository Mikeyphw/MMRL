# MMRL `next`: upstream-sync policy

- Base lineage: **`Mikeyphw/MMRL:lsposed-vector`** at `b8410ce024b921d96c384e7b45d26929625418ab`.
- Shared ancestry: `MMRLApp/MMRL:master` at `6c2b778867f7f66ee2d208ee82887aa2cf9bb0bb`.
- Do **not** use `Mikeyphw/MMRL:master` as the base or merge target for this rewrite.
- Keep historical `lsposed-vector` and open Phase 07 PR #7 unchanged. Do not merge Phase 07 into `next`.
- The rebuild removes Phase 01–06 LSPosed/Vector code, lab/test/CI assets, and Gradle settings specific to that integration.
- Preserve generic `terminal-compat` and `webui-core-compat` Gradle modules; use them via project dependencies. These are **build compatibility bridges**, not yet a shared cross-app library.
- Preserve the fork-only release guard on `.github/workflows/release-ci.yml`, so accidental official upstream releases are not enabled.
- The old LSPosed-only `.devtool.toml` is removed. Configure a new generic MMRL Devtool Android target after hosted compilation validates the branch.

## Regular upstream updates

Run `zsh scripts/sync-upstream-next.zsh --plan` for a preview, then `--apply` to create a separate `sync/upstream-<SHA>` integration branch and a PR targeting **`next`**, not the old fork `master`.

The script performs an ordinary no-force Git merge of `MMRLApp/MMRL:master` into a new branch. It refuses dirty checkouts, preserves conflicts for inspection, and never auto-pushes an unresolved merge. Host CI and manual verification remain requirements before the upstream integration PR is merged.

## Reuse with MMRL-LSPosed

Devtool federation coordinates **multiple repositories** and source locks; it does **not** sync Kotlin code automatically. Reusable platform-neutral code (GitHub API policies, verified downloads, metadata, UI design tokens, parsing) can move later into a versioned `mmrl-shared` Kotlin module, with each app referencing a reviewed pinned version. Do not federate or extract Vector/KernelSU-specific protocol and privileged mutation code into the shared module.

Start with a conservative Devtool federation coordinator *only when the application targets are compiling*: child repositories `MMRL:next` and `MMRL-LSPosed:main`, and a named `validate-both` workflow. Use `federation list`, `inspect`, `preflight`, `plan`, `affected`, and frozen source-lock verification before execution. Do not mistake `federation sync` for upstream Git merge: it is source-lock-driven workspace management, not a general branch integration function.

## Acceptance

This kit does not compile an APK or prove device behavior. Validate the first `next` hosted build and all Gradle modules before making `next` the default branch. The old fork `master`, historical `lsposed-vector`, and separate MMRL-LSPosed repository remain unchanged.
