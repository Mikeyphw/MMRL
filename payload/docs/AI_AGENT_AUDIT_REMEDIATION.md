# MMRL Bug Audit and Remediation Handoff

## Audience

This document is directed at an implementation-oriented AI coding agent. Treat the findings as a prioritized remediation backlog. Preserve existing behavior and tests unless a change is required by a finding. Do not make broad refactors unrelated to these items.

## Validation baseline

The repository was tested through the configured DevTool target:

```text
devtool --target mmrl_android --no-hud --no-clipboard --stdout-payload summary test
```

Result: 444 tests passed, 0 failed, 0 skipped. The run covered platform JVM tests, native contracts, and official-debug app unit tests. It completed with environmental/tooling warnings and eight test-source Kotlin nullability warnings. No production source changes were made during the audit.

Passing tests do not cover the destructive path edge cases or lifecycle races below.

## Priority 0: prevent storage escape and destructive deletion

### Finding

`OperationStoragePolicy.safeOperationId()` replaces disallowed characters but permits exact `.` and `..` values. Those values are passed into `File.resolve()` and then into recursive deletion in:

- `app/src/main/kotlin/com/dergoogler/mmrl/installer/OperationStoragePolicy.kt`
- `app/src/main/kotlin/com/dergoogler/mmrl/installer/OperationStagingStore.kt`
- `app/src/main/kotlin/com/dergoogler/mmrl/installer/UpdateRollbackStore.kt`

An operation ID of `..` can resolve to the parent of the intended store, potentially the application `filesDir`, before `deleteRecursively()` executes.

### Required remediation

1. Reject blank values, `.` and `..`.
2. Prefer opaque directory names derived from a hash of the logical operation ID, or require a UUID format where callers permit it.
3. Before creating or deleting a directory, canonicalize it and require that its direct parent is the canonical store root.
4. Fail closed if canonicalization fails.
5. Apply the same helper to staging, rollback, Tasker review archives, and any other operation-scoped storage.

### Acceptance tests

Add tests for `"."`, `".."`, `"../x"`, empty/whitespace IDs, separator collisions (`a/b` versus `a?b`), and symlinked child directories. Verify that no operation can delete or write outside its store root.

## Priority 1: isolate concurrent downloads

### Finding

`DownloadService` accepts a caller-supplied `TaskItem.operationId` and uses it as the ownership key for `taskJobs`, `activeCalls`, cancellation state, history, and the temporary file path. Two active tasks with the same ID can overwrite each other’s maps, write the same `.part` file, cancel the wrong HTTP call, and clean up the other task.

Relevant code: `app/src/main/kotlin/com/dergoogler/mmrl/service/DownloadService.kt` around task registration, `taskJobs`, `activeCalls`, `cancelRequests`, and `temporaryFile()`.

### Required remediation

1. Add an atomic in-flight ownership gate keyed by operation ID.
2. Reject duplicate active IDs before opening a temporary file.
3. Give every attempt a random attempt ID and use it for temporary files and callback ownership.
4. Make cleanup conditional on ownership so an old attempt cannot remove newer state.
5. Define and test behavior when a terminal history ID is reused; it should be rejected or create a fresh retry operation, never silently reuse a completed row.

### Acceptance tests

Cover duplicate starts, cancellation racing with completion, retrying a terminal operation, and two downloads writing the same destination.

## Priority 1: make callback failures unable to strand operations

### Finding

The success path wraps callbacks with `DownloadCompletionPolicy.runPostCommitBestEffort`, but `onStarted()` and `onFailure()` invoke external listeners directly. A listener exception can prevent history finalization, notification, and `cleanupTask()`.

Relevant code: `DownloadService.kt` around `onStarted()` and `trackedListener.onFailure()`.

### Required remediation

1. Wrap every listener callback, including progress and permission-denied callbacks, as best effort.
2. Keep history finalization and cleanup outside callback failure paths.
3. Put mandatory cleanup in `finally` or an equivalent exception-safe branch.
4. Log callback exceptions without changing the durable download result.

### Acceptance tests

Use listeners that throw from `onStarted`, `onFailure`, progress, and permission denial. Assert that history reaches a terminal state and all task/call/temp-file ownership is released.

## Priority 1: make service destruction recoverable

`DownloadService.onDestroy()` cancels jobs and calls can race with failure handling. Failure handling launches durable history updates in `lifecycleScope`; during service teardown that scope may already be cancelling. This can leave operation rows in a running state until a later recovery pass, or lose the final failure update entirely.

Required changes:

- use an application/process-owned scope for durable finalization, or a non-cancellable finalization path;
- persist attempt state sufficient for process-death recovery;
- reconcile queued/downloading/publishing operations on service startup;
- test teardown during each download phase.

## Priority 2: normalize LSPosed identity consistently

`LsposedRepositorySortPolicy` normalizes package identities, but `LsposedScreens.kt` builds a raw installed-package set and performs raw membership checks for counts, cards, and review dialogs. A package can therefore be pinned as installed while displayed as not installed.

Normalize both the installed set and every lookup with `LsposedIdentity.normalize()`. Use the normalized identity for Compose keys and deterministic duplicate handling as well.

## Priority 2: require confirmation for repository deep links

`MainActivity` immediately persists a repository supplied by a validated deep link. The deep-link container is trusted, but the embedded repository URL may be any HTTPS host. Opening a link can silently add a persistent third-party source.

Recommended changes:

- show the normalized hostname and URL in a confirmation UI;
- persist only after explicit user approval;
- revalidate redirect destinations and consider blocking loopback/private/link-local targets;
- record the source and approval state for repository provenance.

## Priority 3: harden related storage APIs

Audit and unify the filename/ID handling in:

- `TaskerReviewTokens`
- `TaskerAuthorization`
- `TaskerRuntime` log export
- GitHub/LSPosed cache materialization
- download destination and temporary-file helpers

Avoid replacement-based sanitization when uniqueness matters. Use typed opaque IDs, canonical containment checks, and atomic publication. Ensure failed deletion is visible to the caller rather than silently ignored.

## Priority 3: clean test warnings

The DevTool run reported eight Kotlin nullability warnings in release contract tests. They are test-only, but should be cleaned up:

- use `System.getProperty("user.dir") ?: "."`;
- assign `parentFile` to a local after checking it is non-null;
- avoid relying on Java platform-type inference in loops.

## Suggested implementation sequence

1. Add failing regression tests for storage escape and ID collisions.
2. Implement one shared safe-child/opaque-ID helper and migrate both operation stores.
3. Add download ownership/attempt IDs and callback isolation.
4. Add service teardown/recovery tests and durable finalization.
5. Normalize LSPosed identity lookups.
6. Add repository deep-link confirmation and network destination policy.
7. Run the full DevTool test suite, then lint and instrumentation validation.

## Agent constraints

- Do not use destructive cleanup commands on the workspace.
- Do not weaken archive verification, authorization, or provenance checks to make tests pass.
- Preserve existing public behavior unless the behavior is explicitly identified above as unsafe.
- For every fix, add a focused regression test and report the exact DevTool command and result.
- Keep changes scoped; do not edit generated build output or unrelated release metadata.
