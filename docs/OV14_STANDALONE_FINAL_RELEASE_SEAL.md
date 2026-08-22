# OV14 standalone final release seal

OV14 closes the MMRL side of the AshReXcue split. MMRL must validate and release with no AshReXcue runtime, source, manifest, Tasker, module, or checkout relationship.

Required evidence:

- stable Android/JVM/native toolchain gate passes;
- OV09 purge, OV10 product cleanup, and OV13 cross-repository independence gates pass;
- source-hygiene validator passes;
- platform JVM and native contracts pass;
- official-debug unit tests pass;
- final full lint runs with `mmrl.fullLint=true`, dependency lint, warnings-as-errors and abort-on-error;
- instrumentation sources compile;
- personal-use official debug and signed release APKs assemble and are non-empty;
- MMRL's own `ModuleSnapshot` feature and regression coverage remain intact.

Manual replay: `bash scripts/run-mmrl-release-seal.sh`. Connected instrumentation remains optional via `MMRL_RUN_CONNECTED_TESTS=1`.

## Audit hotfix v3 — personal-use distribution

The Play Store build type and validation lane are removed completely. MMRL now seals only `officialDebug` and signed `officialRelease`. CI retains a short-lived validation-only signing key for the release lane; local/personal release builds still require the user's real `signing.properties`. Runtime branches that depended on `IS_GOOGLE_PLAY_BUILD` were simplified to the personal-use/GitHub behavior, and the Play Store-only manifest source set was removed.


## Focused personal-use bug-hunt v4

The repo-wide replay removed the remaining distribution and split residue: obsolete backed-up build configs are deleted, user-facing update copy points only to GitHub Releases, the dead Ash Tasker durable-worker origin is removed, and the stale Ash-named LSPosed test is renamed. The final hygiene gate enforces these conditions.
