# MMRL UI/UX implementation

This snapshot contains the first two implementation slices of the dark, modern, flat, borderless redesign.

## Slice 1 — repository sorting and visual foundation

### Repository sorting

- Replaced duplicated repository comparators with one deterministic `sortedForRepository` pipeline.
- Fixed Size sorting, which previously compared every module against the constant `0`.
- Corrected Updated Time direction so descending means newest first.
- Keeps missing size/date metadata at the end in both directions.
- Preserves selected ordering within pinned update/installed groups.
- Uses deterministic name and module-ID tie breakers.
- Applies the same implementation to `RepositoryViewModel` and `rememberOnlineModules`.
- Defaults new installs to Recently Updated, descending, without hidden pinning.
- Added JVM tests for size, date, missing metadata, and pin behavior.

### Dark flat visual foundation

- Added a neutral near-black MMRL Base dark palette with restrained blue accents.
- New installs default to dark mode and the MMRL Base palette instead of dynamic color.
- Blur is disabled by default.
- Removed automatic toolbar dividers, card hover outlines, outlined-card borders, and default card elevation.
- Reduced global corner radii and repository spacing.

## Slice 2 — repository rows, Installed groups, and Updates

### Repository list redesign

- Replaced separate card-heavy compact and detailed implementations with one shared borderless row.
- Preserves optional cover images and module icons.
- Prioritizes module name, version, developer, repository, category, compatibility, and install/update status.
- Adds concise indicators for WebUI, verified publishers, and anti-features.
- Compact mode omits descriptions; detailed mode keeps a two-line description without restoring card chrome.

### Visible sorting state

- Shows the active sort field and direction directly in the repository toolbar.
- Shows active search/filter count and whether pinning is active.
- Adds a directly visible reset action whenever ordering, pinning, or filtering differs from defaults.
- Keeps explicit ascending/descending and pin controls in the sheet.
- Scrolls to the top and displays immediate snackbar feedback after ordering changes.

### Installed screen redesign

- Adds a compact root/device status summary with manager, active module count, update count, root availability, and pending reboot state.
- Groups modules into Needs attention, Updates available, Enabled, Disabled, and Pending removal.
- Uses a deterministic grouping policy so each module appears exactly once.
- Replaces large cards with flat compact rows.
- Keeps enable/disable visible while moving WebUI, action, update, remove, and restore actions into an overflow menu.
- Adds unit tests for grouping priority, exclusivity, and empty-group removal.

### Dedicated module Updates destination

- Adds `ModuleUpdatesScreen`, reachable from the Installed toolbar with an update-count badge.
- Shows installed → available version, changelog preview, source, compatibility, boot-script metadata, SELinux/property metadata, and reboot impact.
- Reuses the existing per-version review/download flow for individual updates.
- Adds a review-all sheet that excludes incompatible updates and hands compatible downloads to the existing bulk installer.
- Uses a unique generated destination name to avoid colliding with the existing Settings > Updates screen.

## Current metadata limitation

MMRL does not currently persist the repository that originally installed each local module, so this slice can show the proposed update source but cannot reliably claim that the source changed. Likewise, exact added/removed file diffs require downloading and inspecting the update ZIP; this slice surfaces boot-script and sensitive-change metadata available before download. Archive-diff review should be implemented as the next installer-focused slice.

## Validation performed

- `git diff --check` passes.
- All Android resource XML files parse successfully.
- Changed Kotlin files reference existing strings and drawables.
- Compose Destinations function names were checked for generated-name collisions.
- Repository sorting was compiled and exercised with Kotlin/JVM stubs.
- Installed grouping was compiled and exercised with Kotlin/JVM stubs.
- Added JUnit tests for repository sorting and installed grouping.
- Full Gradle tests could not run because Gradle 9.1.0 is not cached and the sandbox cannot resolve `services.gradle.org`.

## Slice 3 — persistent Activity and operation history

### Persistence and recovery

- Added a Room-backed operation history model with an explicit database migration from schema 15 to 16.
- Persists operation kind, status, title, module/source metadata, timestamps, progress, reboot state, retry/rollback contracts, errors, and technical output.
- Retains the latest 500 entries while preserving active operations.
- Caps individual log lines and total stored log size to prevent unbounded database growth.
- Recovers stale running operations as interrupted failures.
- Marks reboot-required operations complete after the next successful device boot.

### Operation coverage

- Downloads record source, destination, progress, success, failure, and stack traces.
- Installs and updates record validation failures, archive source, terminal output, final state, and reboot requirements.
- Enable, disable, remove, restore, and module action operations record backend execution and terminal/root failures.
- Newly installed modules expose a safe remove rollback. Enable/disable operations expose their inverse action. Update rollback is intentionally unavailable until previous archives are retained.

### Activity UI

- Added Activity to primary navigation with a pending-reboot badge.
- Groups the timeline by day and supports All, Running, Failed, and Pending reboot filters.
- Uses compact flat rows with progress, status, retry, rollback, and reboot indicators.
- Provides a detail sheet with source/destination metadata, errors, scrollable technical logs, copy/share, deletion, retry, and safe rollback actions.
- Clearing history removes completed entries while preserving running operations.

### Retry and rollback behavior

- Download retry reuses the original URL and destination filename.
- Install retry reopens the original URI set.
- Module action retry reopens the action terminal.
- Module state retries and rollbacks execute through the active root backend and create linked child history records.
- Every retry and rollback remains auditable through its parent operation ID.

## Phase 4 — Module details, reviewed transactions, rollback, and adaptive states

Implemented in this phase:

- Rebuilt module details into Overview, Changes, Files, and Details destinations.
- Added decision-first metadata for compatibility, current/available version, source, reboot, WebUI, verification, and anti-features.
- Added adaptive details navigation: tabs on phones and a persistent side rail on large screens.
- Replaced immediate individual install/update actions with Review → Download → Verify → Inspect → Ready → Install.
- Added archive SHA-256 calculation and bounded ZIP inspection for traversal, excessive expansion, scripts, native binaries, APKs, SELinux policy, system properties, and remote execution references.
- Persisted Review, Download, Verify, Inspect, Stage, Install, Result, and Rollback phases in Activity.
- Linked download and install records with parent operation IDs.
- Added a bounded two-slot download queue, queued notifications, notification cancellation, Activity cancellation, and a Downloads history filter.
- Made download notifications open Activity directly.
- Added update rollback archives for the previous installed module version before staging.
- Added automatic rollback after a failed update and manual restore from Activity when the retained archive remains available.
- Shows rollback intent before an update and records archive path, previous version, target version, inspection summary, and rollback result.
- Added explicit repository loading, cached-offline, empty search, empty repository, refresh, and refresh-failure states.
- Added tablet/foldable list-detail layout for Updates and adaptive side-by-side detail navigation.

Safety limits:

- Archive inspection blocks traversal, more than 20,000 entries, entries larger than 256 MiB, expansion beyond 1 GiB, and suspicious compression ratios.
- Rollback archives are capped at 20 retained archives, 20,000 files, and 512 MiB per backup.
- Update rollback is only offered when an actual retained archive exists. A failed backup is logged rather than presented as guaranteed rollback.

## Phase 5 — reliable downloads, installed identity, update alerts, and batch UX

### Atomic and recoverable downloads

- Downloads are written to an app-cache `.part` file first and are published to the configured Downloads directory only after a successful, non-empty response.
- HTTP failures, cancellation, verification failures, and destination-copy failures remove temporary and partially published output.
- Legacy zero-byte ZIPs are detected and removed automatically before retrying.
- Existing non-empty downloads remain protected from accidental overwrite.
- Published byte counts are checked against the staged archive before success is reported.
- Output streams are closed on both HTTP success and failure paths.
- Each request has a unique request identity, and listener storage is safe for concurrent batch downloads.
- Downloading no longer depends on notification permission; missing notification permission only suppresses notifications.

### Installed-module identity

- Repository entries now correlate with installed modules by normalized module ID rather than author metadata.
- Matching is case-insensitive and ignores surrounding whitespace.
- This recognizes manually installed modules and the same module published by another repository even when author/source metadata differs.
- Update-tracking tags are normalized and duplicate case variants are consolidated.

### Module update notifications

- Background module update checks are controlled by the existing Updates settings and support 1–72 hour intervals.
- A Check now action triggers an immediate refresh without changing the selected frequency.
- Update matching uses normalized module IDs and the newest available version across enabled repositories.
- Failed repositories fall back to their cached module metadata instead of suppressing all update checks.
- Individual module updates can be ignored from the Updates destination or module details.
- Ignoring an update cancels its notification immediately; undo/re-enable clears stale notification markers so a future check can notify correctly.
- Notifications deep-link to the dedicated module Updates destination and stale version notifications are removed.

### Batch download selection

- The batch sheet defaults to all modules selected and provides per-module checkboxes.
- Adds Select all, Clear, selected count, combined size, and compact removable rows.
- Separates Download selected from Review and install selected.
- Downloads launch as one batch while the service enforces a bounded two-download queue.
- Active batches can be cancelled from the sheet, notifications, or Activity.
- Partial success is explicit: completed modules are removed from the basket, failed modules remain available for retry, and successful selected items can continue to installation when appropriate.

## Phase 6 — Tasker plugin, read-only and download automation

Implemented with the official Tasker plugin library (`0.4.10`):

- Get module status
- List installed modules
- Check updates, with optional repository refresh
- Get operation result
- Queue module download by repository ID or direct URL
- Cancel queued/running download
- Export technical operation log through a read-only content URI
- Update-discovered event
- Operation-failed event
- Structured scalar, array, and JSON outputs
- Tasker-origin chips and metadata in Activity
- Database migration 17 → 18 for operation origin
- Existing atomic download queue, cancellation, foreground notifications, and Activity links reused
- Input validation and safe filename/URL policy
- Focused tests for automation URL and filename validation

See `TASKER_SUPPORT.md` for the action, event, and variable contract.

## Tasker phases 3 and 4

- Controlled enable, disable, remove, restore, and predefined module-action requests.
- Per-capability settings, approval policies, and normalized module allowlist.
- Approval notifications plus Activity-based approve/deny fallback.
- Prepare Reviewed Install and Execute Reviewed Install actions.
- Thirty-minute, SHA-256-bound, single-claim review tokens.
- Automatic archive safety classification; non-routine archives always require explicit approval.
- Update backup, automatic failure restoration, manual rollback reporting, and Activity linkage.
- No arbitrary shell command surface.

## Theme system and fork identity

- Stable palette IDs with legacy positional-ID migration.
- MMRL, Dracula, Sweet Dark, Nord, Monokai, and retained legacy palettes.
- Built-in, Wallpaper Accent, Full Monet, and validated Custom JSON color sources.
- Flat, Soft Tonal, and High Contrast surface styles.
- Standard, Medium, and High text contrast.
- OLED pure-black background, accent intensity, enhanced status distinction, and battery-saver dark mode.
- Full-screen apply/cancel preview using real MMRL components and semantic status roles.
- Validated custom-theme JSON editor, clipboard export, and Android document import/export.
- Persistent one-time migration from positional theme IDs to stable palette IDs.
- Semantic status colors and stable WebUI CSS variables.
- Fork application ID `com.mikeyphw.mmrl` with distinct launcher/APK naming and fork-specific intent actions.

## UI/UX Phase 2 — task-oriented recovery workflow

- Replaced Recovery Center's internal Overview/Guidance/Quarantine/Sessions/Diagnostics categories with Check status, Restore modules, Find culprit, Review trial, History, and Advanced tasks.
- Moved technical compatibility, release-gate, state-repair, export, and audit controls behind Advanced.
- Quarantine now opens conservative, balanced, rapid, and one-module guarded plan previews directly instead of redirecting to Guidance.
- Added keyed operation state so refresh, install preparation, recovery plans, trial decisions, trust changes, settings, repair, export, and guidance feedback report progress independently.
- Made recovery confirmation and preview dialogs bounded, scrollable, and IME-safe.
- Replaced inert AshReXcue `AssistChip` status decorations with noninteractive `StatusPill` surfaces.
- Made module intelligence compact by default with expandable evidence and update-safety details.
- Added focused unit tests for independent operation targets and refresh isolation.
- Reworked `TaskerRootRequest` persistence to round-trip Ash automation tokens and idempotency keys through Kotlin serialization JSON instead of mockable Android `org.json` during host unit tests.

## UI/UX Phases 3 and 4 — accessibility, localization, and adaptive polish

- Moved the main Recovery Center, Guided Recovery, AshReXcue module-detail cards, and Installed Ash protection filter copy into Android resources.
- Added shared flat UI primitives for section cards, clickable rows, status pills, metadata rows, and scrollable dialog content.
- Added selected/tab semantics to Recovery, Activity, and Installed Ash filter chips; headings to section/day/detail titles; noninteractive status semantics to status pills; and minimum 48dp row targets for compact Activity rows.
- Added a reduced-motion composition local based on Android animator-duration scale and applied it to app navigation transitions and toolbar alpha animation.
- Added expanded-width list/detail layouts for Recovery session history and Activity operation details while preserving dialogs/sheets on compact layouts.
- Consolidated Recovery, Activity, Ash module intelligence, and Ash integration cards onto the shared flat/status components and semantic status colors where status meaning is known.

Screenshot regression testing and Fastlane screenshot replacement are still pending because this project does not currently include a screenshot-test framework, `app/src/androidTest`, or Roborazzi/Paparazzi dependencies. Adding that harness should be handled as a separate build/dependency change.
